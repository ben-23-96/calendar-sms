package com.ben;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.twilio.twiml.messaging.Message;
import com.twilio.twiml.MessagingResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReceiveMessage implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.defaultClient();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        // Log the raw body for debugging
        String requestBody = request.getBody();
        context.getLogger().log("Received Twilio message: " + requestBody);

        // Decode the form data from the request body
        List<NameValuePair> params = URLEncodedUtils.parse(requestBody, StandardCharsets.UTF_8);
        // Convert List to Map for easier access
        Map<String, String> paramMap = new HashMap<>();
        for (NameValuePair param : params) {
            paramMap.put(param.getName(), param.getValue());
        }
        // Extract message text
        String receivedMessageText = paramMap.getOrDefault("Body", "No message text!");

        // Parse the message to extract date and event name
        String[] messageParts = receivedMessageText.split(" ", 2);
        if (messageParts.length < 2) {
            String replyMessage = "Failed to parse message. Use format DD-MM-YYYY event name.";
            return createResponse(replyMessage, 200);
        }
        String eventDate = messageParts[0];
        String eventName = messageParts[1];

        // Validate the event date
        if (!isFutureDate(eventDate)) {
            String replyMessage = "The provided event date is not in the correct format or not a future date. Use format DD-MM-YYYY.";
            return createResponse(replyMessage, 200);

        }
        // Add to DynamoDB
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("Date", new AttributeValue().withS(eventDate));
        item.put("eventName", new AttributeValue().withS(eventName));

        try {
            dynamoDB.putItem(new PutItemRequest().withTableName("calendarTable").withItem(item));
        } catch (AmazonDynamoDBException e) {
            e.printStackTrace();
        }

        // Create reply to indicate success
        String replyMessage = "Event '" + eventName + "' on " + eventDate + " has been added to the calendar.";
        return createResponse(replyMessage, 200);
    }

    // Method to check if the event date is in the correct format and is a future
    // date
    private boolean isFutureDate(String eventDateStr) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        dateFormat.setLenient(false);

        try {
            Date eventDate = dateFormat.parse(eventDateStr);
            Date currentDate = new Date(); // This gets the current date and time

            // Check if the event date is after today's date (considering date only)
            return eventDate.after(currentDate);
        } catch (ParseException e) {
            // The date is in an invalid format or not a real date
            return false;
        }
    }

    private APIGatewayProxyResponseEvent createResponse(String replyMessage, int statusCode) {
        // Create TwiML message
        Message message = new Message.Builder(replyMessage).build();
        MessagingResponse twiMLResponse = new MessagingResponse.Builder().message(message).build();
        // Prepare and return the response
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setHeaders(Collections.singletonMap("Content-Type", "text/xml"));
        response.setBody(twiMLResponse.toXml());
        return response;
    }
}
