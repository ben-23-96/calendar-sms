package com.ben;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SendMessage implements RequestHandler<Object, String> {

    private final AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.defaultClient();
    private static final String ACCOUNT_SID = System.getenv("TWILIO_ACCOUNT_SID");
    private static final String AUTH_TOKEN = System.getenv("TWILIO_AUTH_TOKEN");
    private static final String FROM_PHONE_NUMBER = System.getenv("TWILIO_FROM_PHONE_NUMBER");
    private static final String TO_PHONE_NUMBER = System.getenv("TWILIO_TO_PHONE_NUMBER");

    @Override
    public String handleRequest(Object event, Context context) {
        // Initialize the Twilio client
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        // Check for events in calendar that are today
        List<Item> calendarEventsToday = checkForTodaysCalendarEvents();

        StringBuilder messageLog = new StringBuilder();

        for (Item calendarEvent : calendarEventsToday) {
            // Get the event and make message string
            String eventName = calendarEvent.getString("eventName");
            String messageBody = "Reminder: " + eventName + " is happening today!";
            try {
                // Send the SMS
                Message message = Message.creator(
                        new PhoneNumber(TO_PHONE_NUMBER),
                        new PhoneNumber(FROM_PHONE_NUMBER),
                        messageBody)
                        .create();

                messageLog.append("Message sent with SID: ").append(message.getSid()).append("\n");
            } catch (Exception e) {
                context.getLogger().log("Error sending Twilio Message for event " + eventName + ": " + e.getMessage());
                messageLog.append("Failed to send message for: ").append(eventName).append("\n");
            }
        }

        return messageLog.toString();
    }

    private List<Item> checkForTodaysCalendarEvents() {
        // Initialise the client
        DynamoDB dynamoDBClient = new DynamoDB(dynamoDB);
        // Get the table
        Table table = dynamoDBClient.getTable("calendarTable");
        // Get the current date in LocalDate format
        LocalDate currentDate = LocalDate.now();

        // Define a DateTimeFormatter matching your date format in the database
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        List<Item> calendarEventsToday = new ArrayList<>();

        try {
            // Create a ScanSpec
            ScanSpec scanSpec = new ScanSpec();

            // Perform the scan operation
            ItemCollection<ScanOutcome> calendar = table.scan(scanSpec);
            Iterator<Item> calendarIterator = calendar.iterator();

            // Iterate through the results
            while (calendarIterator.hasNext()) {
                Item item = calendarIterator.next();
                // Get the date of the calendar event
                String eventDateStr = item.getString("Date");
                // Convert the date string to a LocalDate
                LocalDate eventDate = LocalDate.parse(eventDateStr, formatter);

                // Check if the date is today, if so add to the list to be returned
                if (currentDate.equals(eventDate)) {
                    calendarEventsToday.add(item);
                }
            }
        } catch (Exception e) {
            System.err.println("Unable to scan the table:");
            System.err.println(e.getMessage());
        }

        return calendarEventsToday;
    }
}