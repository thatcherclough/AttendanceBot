package com.thatcherdev.attendancebot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AttendanceBot {

	private static String configFile = null;
	private static PrintWriter out = null;
	private static EmailSender emailSender = null;
	private static final String help = "AttendanceBot: A bot used to automate filling out Google Forms for school attendance (1.0.1)\n\nUsage:\n\tjava -jar attendancebot" +
			".jar [-h] [-v] [-f CONFIG JSON FILE]\n\nArguments:\n\t-h,  --help\t\tDisplay this message.\n\t-v,  --version\t\tDisplay current version.\n\t-f,  " +
			"--file\t\tSpecify JSON configuration file. (See README for example)";

	/**
	 * Starts AttendanceBot based on command line arguments {@code args}.
	 *
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		try {
			if (args.length == 0)
				throw new Exception();

			for (int k = 0; k < args.length; k++)
				if (args[k].equals("-h") || args[k].equals("--help")) {
					throw new Exception();
				} else if (args[k].equals("-v") || args[k].equals("--version")) {
					System.out.println(help.substring(0, help.indexOf("\n")));
					System.exit(0);
				} else if (args[k].equals("-f") || args[k].equals("--file"))
					configFile = args[++k];

			if (configFile == null || !new File(configFile).exists())
				throw new Exception();
		} catch (Exception e) {
			System.out.println(help);
			System.exit(0);
		}

		try {
			StringBuilder jsonString = new StringBuilder();
			Scanner in = new Scanner(new File(configFile));
			while (in.hasNextLine())
				jsonString.append(in.nextLine()).append("\n");
			in.close();

			JSONObject json = new JSONObject(jsonString.toString());

			out = new PrintWriter(new File(json.getString("log_file")));
			if (json.has("email_notifications")) {
				JSONObject emailNotificationsJson = json.getJSONObject("email_notifications");
				if (emailNotificationsJson.has("sender_email") && emailNotificationsJson.has("sender_password") && emailNotificationsJson.has("receiver_email")) {
					String senderEmail = emailNotificationsJson.getString("sender_email");
					String senderPassword = emailNotificationsJson.getString("sender_password");
					String receiverEmail = emailNotificationsJson.getString("receiver_email");

					if (EmailSender.checkCreds(senderEmail, senderPassword))
						emailSender = new EmailSender(senderEmail, senderPassword, receiverEmail);
					else
						log("Email notification credentials not valid. Log messages will not be emailed.");
				}
			}
			String chromedriverExecutable = json.getString("chromedriver_executable");
			LocalTime timeToRun = LocalTime.parse(json.getString("time_to_run"));
			long defaultWait = Integer.parseInt(json.getString("default_wait_in_millis"));

			log("Bot started");

			LocalDateTime nextTimeToRun = LocalDate.now().atTime(timeToRun);
			long nextTimeToRunMillis = nextTimeToRun.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			long nowMillis = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

			if (nowMillis > nextTimeToRunMillis)
				nextTimeToRun = nextTimeToRun.plusDays(1);

			while (true) {
				if (nextTimeToRun.getDayOfWeek().getValue() < 6) {
					log("Set to run " + nextTimeToRun);

					waitUntil(nextTimeToRun);
					log("Running bot on " + nextTimeToRun);

					ArrayList<String> studentInfoList = new ArrayList<>();
					JSONArray studentInfoJsonArray = json.getJSONArray("student_info");
					for (int k = 0; k < studentInfoJsonArray.length(); k++)
						studentInfoList.add(studentInfoJsonArray.get(k).toString());
					if (studentInfoList.size() > 1 && json.has("randomize_order_of_students") && json.getString("randomize_order_of_students").equals("true"))
						Collections.shuffle(studentInfoList);

					for (int k = 0; k < studentInfoList.size(); k++) {
						JSONObject studentInfoJson = new JSONObject(studentInfoList.get(k));

						Map<String, String> cssSelectorsWithActions = new LinkedHashMap<>();
						JSONArray cssSelectorsWithActionsJson = studentInfoJson.getJSONArray("cssSelectorsWithActions");
						for (int i = 0; i < cssSelectorsWithActionsJson.length(); i++) {
							JSONObject cssSelectorWithAction = cssSelectorsWithActionsJson.getJSONObject(i);
							String key = cssSelectorWithAction.keys().next();
							String value = cssSelectorWithAction.getString(key);
							cssSelectorsWithActions.put(key, value);
						}

						Bot bot = new Bot(chromedriverExecutable, studentInfoJson.getString("email"), studentInfoJson.getString("password"),
								studentInfoJson.getString("google_class_link"), cssSelectorsWithActions, defaultWait);
						bot.start();

						if ((k != (studentInfoList.size() - 1)) && (json.has("run_duration_in_minutes") && json.getString("run_duration_in_minutes").matches("[0-9]+")))
							Thread.sleep((long) (((Integer.parseInt(json.getString("run_duration_in_minutes")) * 60 * 1000) / (studentInfoList.size() - 1)) - (defaultWait * (7.2 + cssSelectorsWithActions.size()))));
					}
					log("Finished for " + nextTimeToRun);
				} else
					log(nextTimeToRun + " is a weekend, forwarding execution 1 day");
				nextTimeToRun = nextTimeToRun.plusDays(1);
			}
		} catch (Exception e) {
			String logMessage = "An error has occurred";
			if (e.getMessage() != null)
				logMessage = logMessage + ": " + e.getMessage();

			log(logMessage);
			e.printStackTrace();
			if (out != null) {
				e.printStackTrace(out);
				out.flush();
				out.close();
			}
		}
	}

	/**
	 * Sleeps until {@code date} is reached.
	 *
	 * @param date date to sleep until
	 * @throws InterruptedException
	 */
	private static void waitUntil(LocalDateTime date) throws InterruptedException {
		long dateMillis = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		long nowMillis = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		long difference = dateMillis - nowMillis;
		Thread.sleep(Math.abs(difference));
	}

	/**
	 * Logs {@code logMessage} to the console and {@link #out}. Emails {@code logMessage} using {@link #emailSender}.
	 *
	 * @param logMessage message to log and email
	 */
	public static void log(String logMessage) {
		try {
			String timeString = "[" + DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss").format(LocalDateTime.now()) + "] ";
			logMessage = timeString + logMessage;

			System.out.println(logMessage);
			if (out != null) {
				out.println(logMessage);
				out.flush();
			}
			if (emailSender != null)
				emailSender.sendEmail(logMessage);
		} catch (Exception ignored) {
		}
	}
}