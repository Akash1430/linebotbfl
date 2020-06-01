package com.bfl.bot;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.ExecutionException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.profile.UserProfileResponse;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

@SpringBootApplication
@LineMessageHandler
public class LinebotbflApplication {

	String loginAccessToken = null;
	String contactId = null;
	
    static final String USERNAME = "pawan@icbworks.com.dev";
    static final String PASSWORD = "ANAvi301414Pq6DqIAFEhlwZyJ2ptEllFF5";
    static final String LOGINURL = "https://login.salesforce.com";
    static final String GRANTSERVICE = "/services/oauth2/token?grant_type=password";
    static final String CLIENTID = "3MVG9tzQRhEbH_K3IUveppUwV9Prgam_9G_vGCIRC2qv9s.VCaL_hbp3YKi40ra9f0iecl_oATFIqkQWbAYsz";
    static final String CLIENTSECRET = "B3DE286F206E6C2D8590857733717E4BC2821AE4F59544A37391B1BF6D18C79C";

    private static String REST_ENDPOINT = "/services/data";
    private static String API_VERSION = "/v48.0";
    private static String baseUri;
    private static Header oauthHeader;
    private static Header prettyPrintHeader = new BasicHeader("X-PrettyPrint", "1");
    
    public static void main(String[] args) {
		SpringApplication.run(LinebotbflApplication.class, args);
	}

    @EventMapping
    public TextMessage handleTextMessageEvent(MessageEvent<TextMessageContent> event) {
		String userName = null;
		
		CreateConnection();
		final String followedUserId = event.getSource().getUserId();
		String originalMessageText = event.getMessage().getText().toUpperCase();
		String replyBotMessage = getMessage(originalMessageText);
		
		 if(replyBotMessage == null) { 
			 replyBotMessage = "An agent will contact you soon with your query.";
			 //Save the keyword in line admin
			 createNewKeyword(originalMessageText); 
		 }
		 
		 contactId = getContactId(followedUserId);
		 userName = getUserName(followedUserId);
		 if(contactId == null) {
			 createContact(followedUserId, userName);
		 }else {
			 updateContact(followedUserId, userName);
		 }
		 logATask(originalMessageText, replyBotMessage);
		  
        
        return new TextMessage(replyBotMessage);
    }

	@EventMapping
    public void handleDefaultMessageEvent(Event event) {
        System.out.println("event: " + event);
    }
 
    public String getMessage(String originalMessage) 
    {
    	if(originalMessage.contains(" ")) {
    		originalMessage = originalMessage.replaceAll("\\s+","+");
    		System.out.println("replace white space with + sign: " + originalMessage);
    	}
    	 String uri = baseUri + "/query/?q=Select+" + "keywordvalue__c+" + "From+" + "knowlegebase__c+" + "Where+" + "keyword__c+"
                 + "=+" + "'" + originalMessage + "'";
        System.out.println("url sending to sf: " + uri);
        try {

            HttpClient httpClient = HttpClientBuilder.create().build();

            try {

                HttpGet httpGet = new HttpGet(uri);
                httpGet.addHeader(oauthHeader);
                httpGet.addHeader(prettyPrintHeader);
                int statusCode = 0;
                HttpResponse response;
                try {
                    response = httpClient.execute(httpGet);
                    System.out.println("response is :"+ response);
                    statusCode = response.getStatusLine().getStatusCode();
                } catch (Exception e) {
                    System.out.println(uri + " error ee: " + e.toString());
                    return null;
                }

                if (statusCode == 200) {
                    String responseString = EntityUtils.toString(response.getEntity());
                    JSONObject jsonObject = new JSONObject(responseString);
                    String result = null;
                    JSONArray jsonArray = jsonObject.getJSONArray("records");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        result = jsonObject.getJSONArray("records").getJSONObject(i).getString("value__c");
                    }
                    return result;
                } else {
                	System.out.println(uri + "status code: " + statusCode);
                    return null;
                }
            } catch (Exception e) {
            	System.out.println("error e: " + e.toString());
                return null;
            }

        } catch (Exception e) {
        	System.out.println("error: " + e.getStackTrace().toString());
            return null;
        }
    }    
    
    public void CreateConnection() {
    	
        HttpClient httpclient = HttpClientBuilder.create().build();

        String loginURL = LOGINURL + GRANTSERVICE + "&client_id=" + CLIENTID + "&client_secret=" + CLIENTSECRET
                + "&username=" + USERNAME + "&password=" + PASSWORD;

        // Login requests must be POSTs
        HttpPost httpPost = new HttpPost(loginURL);
        HttpResponse response = null;

        try {
            // Execute the login POST request
            response = httpclient.execute(httpPost);
        } catch (ClientProtocolException cpException) {
            cpException.printStackTrace();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        // verify response is HTTP OK
        final int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != HttpStatus.SC_OK) {
            System.out.println("Error authenticating to Force.com: " + statusCode);
        }

        String getResult = null;
        try {
            getResult = EntityUtils.toString(response.getEntity());
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        JSONObject jsonObject = null;
        String loginInstanceUrl = null;

        try {
            jsonObject = (JSONObject) new JSONTokener(getResult).nextValue();
            loginAccessToken = jsonObject.getString("access_token");
            loginInstanceUrl = jsonObject.getString("instance_url");
        } catch (JSONException jsonException) {
            jsonException.printStackTrace();
        }

        baseUri = loginInstanceUrl + REST_ENDPOINT + API_VERSION;
        oauthHeader = new BasicHeader("Authorization", "OAuth " + loginAccessToken);
        System.out.println("oauthHeader1: " + oauthHeader);
        System.out.println("\n" + response.getStatusLine());
        System.out.println("Successful login");
        System.out.println("instance URL: " + loginInstanceUrl);
        System.out.println("access token/session ID: " + loginAccessToken);
        System.out.println("baseUri: " + baseUri);
    }
    
    public String getContactId(String followedUserId) {
    	
        String uri = baseUri + "/query/?q=Select+" + "Id+" + "From+" + "Contact+" + "Where+" + "LineExternalId__c+"
                + "=+" + "'" + followedUserId + "'";
        System.out.println("url sending to sf: " + uri);
        
        try {

            HttpClient httpClient = HttpClientBuilder.create().build();

            try {

                HttpGet httpGet = new HttpGet(uri);
                httpGet.addHeader(oauthHeader);
                httpGet.addHeader(prettyPrintHeader);
                int statusCode = 0;
                HttpResponse response;
                try {
                    response = httpClient.execute(httpGet);
                    System.out.println("Contact response is :"+ response);
                    statusCode = response.getStatusLine().getStatusCode();
                } catch (Exception e) {
                    System.out.println(uri + "Contact error ee: " + e.toString());
                    return null;
                }

                if (statusCode == 200) {
                    String responseString = EntityUtils.toString(response.getEntity());
                    JSONObject jsonObject = new JSONObject(responseString);
                    String result = null;
                    JSONArray jsonArray = jsonObject.getJSONArray("records");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        result = jsonObject.getJSONArray("records").getJSONObject(i).getString("Id");
                    }
                    return result;
                } else {
                	System.out.println(uri + "Contact status code: " + statusCode);
                    return null;
                }
            } catch (Exception e) {
            	System.out.println("Contact error e: " + e.toString());
                return null;
            }

        } catch (Exception e) {
        	System.out.println("Contact error: " + e.getStackTrace().toString());
            return null;
        }
    }

    private String getUserName(String followedUserId) {
		  LineMessagingClient client = LineMessagingClient.builder(
				  "iqlit3aTR2DgK328eKrxVoGQQGmI3Y11iNUgBdd2622L+t8hKKjjABRc9CJgTR7ShFzDyQ4Rw392PJinD86MXxsRY4ojeftt3WIIma9DQ0B6NcGQ1yBThbZYt/IysJP5IKmq8qTq+wrBLWVmI9ndWwdB04t89/1O/w1cDnyilFU=")		  .build(); 
		  UserProfileResponse userProfileResponse = null; 
		  try {
			  userProfileResponse = client.getProfile(followedUserId).get(); 
			  return userProfileResponse.getDisplayName(); 
		  } catch (InterruptedException | ExecutionException e) {
			  e.printStackTrace(); 
			  return null;
		  }
    }
    
    public void createContact(String lineUserId, String userName) {
    	
        String uri = baseUri + "/sobjects/Contact/";
        try {

            // create the JSON object containing the new contact details.
            JSONObject contact = new JSONObject();
            //contact.put("FirstName", userName);
            contact.put("LastName", userName);
            contact.put("LineExternalId__c", lineUserId);

            System.out.println("JSON for contact record to be inserted:\n" + contact.toString(1));

            // Construct the objects needed for the request
            HttpClient httpClient = HttpClientBuilder.create().build();

            HttpPost httpPost = new HttpPost(uri);
            httpPost.addHeader(oauthHeader);
            httpPost.addHeader(prettyPrintHeader);
            // The message we are going to post
            StringEntity body = new StringEntity(contact.toString(1));
            body.setContentType("application/json");
            httpPost.setEntity(body);

            // Make the request
            HttpResponse response = httpClient.execute(httpPost);

            // Process the results
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 201) {
                String response_string = EntityUtils.toString(response.getEntity());
                JSONObject json = new JSONObject(response_string);
                contactId = json.getString("id");
                System.out.println("Contact Insertion successful. Status code returned is " + statusCode);
            } else {
                System.out.println("Contact  Insertion unsuccessful. Status code returned is " + statusCode);
            }
        } catch (JSONException e) {
            System.out.println("Issue creating JSON or processing results");
            e.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (NullPointerException npe) {
            npe.printStackTrace();
        }
    }

    public void updateContact(String lineUserId, String userName) {
    	
        String uri = baseUri + "/sobjects/Contact/"+contactId + "?_HttpMethod=PATCH";
        try {

            // create the JSON object containing the new contact details.
            JSONObject contact = new JSONObject();
            //contact.put("FirstName", userName);
            contact.put("LastName", userName);
            contact.put("LineExternalId__c", lineUserId);

            //System.out.println("JSON for contact record to be updated:\n" + contact.toString(1));

            // Construct the objects needed for the request
            HttpClient httpClient = HttpClientBuilder.create().build();

            HttpPost httpPost = new HttpPost(uri);
            httpPost.addHeader(oauthHeader);
            httpPost.addHeader(prettyPrintHeader);
            // The message we are going to post
            StringEntity body = new StringEntity(contact.toString(1));
            body.setContentType("application/json");
            httpPost.setEntity(body);

            // Make the request
            HttpResponse response = httpClient.execute(httpPost);

            // Process the results
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 201) {
                System.out.println("Contact Update successful. Status code returned is " + statusCode);
            } else {
                System.out.println("Contact  Update unsuccessful. Status code returned is " + statusCode);
            }
        } catch (JSONException e) {
            System.out.println("Issue creating Contact Update JSON or processing results");
            e.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (NullPointerException npe) {
            npe.printStackTrace();
        }
    }
    
    private void logATask(String originalMessageText, String replyBotMessage) {
    	String uri = baseUri + "/sobjects/Task/";
    	
    	try {
            JSONObject task = new JSONObject();
            task.put("Description", "Keyword: "+ originalMessageText + "\n" + "Response: " + replyBotMessage);
            task.put("ActivityDate", LocalDate.now());
            task.put("Priority", "Normal");
            task.put("Status", "Completed");
            task.put("Subject", "Line Call");
            task.put("TaskSubtype", "Call");
            task.put("WhoId", contactId);
            System.out.println("JSON for Task record to be inserted:\n" + task.toString(1));

            // Construct the objects needed for the request
            HttpClient httpClient = HttpClientBuilder.create().build();
            HttpPost httpPost = new HttpPost(uri);
            httpPost.addHeader(oauthHeader);
            httpPost.addHeader(prettyPrintHeader);
            // The message we are going to post
            StringEntity body = new StringEntity(task.toString(1));
            //System.out.println("body format for task: " +body);
            body.setContentType("application/json");
            httpPost.setEntity(body);

            // Make the request
            HttpResponse response = httpClient.execute(httpPost);
            
            // Process the results
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 201) {
                //String response_string = EntityUtils.toString(response.getEntity());
                //JSONObject json = new JSONObject(response_string);
                //System.out.println("Create Task Successful: " +json);
            } else {
                //System.out.println("Insertion unsuccessful. Status code returned is " + statusCode);
            }
		} catch (Exception e) {
			// TODO: handle exception
		}
		
	}

	private void createNewKeyword(String originalMessageText) {
		// TODO Auto-generated method stub
        String uri = baseUri + "/sobjects/knowlegebase__c/";
        try {

            // create the JSON object containing the new contact details.
            JSONObject knowledgeBase = new JSONObject();
            //contact.put("FirstName", userName);
            knowledgeBase.put("Name", originalMessageText);
            knowledgeBase.put("keyword__c", originalMessageText);

            System.out.println("JSON for knowlegebase__c record to be inserted:\n" + knowledgeBase.toString(1));

            // Construct the objects needed for the request
            HttpClient httpClient = HttpClientBuilder.create().build();

            HttpPost httpPost = new HttpPost(uri);
            httpPost.addHeader(oauthHeader);
            httpPost.addHeader(prettyPrintHeader);
            // The message we are going to post
            StringEntity body = new StringEntity(knowledgeBase.toString(1));
            body.setContentType("application/json");
            httpPost.setEntity(body);

            // Make the request
            HttpResponse response = httpClient.execute(httpPost);

            // Process the results
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 201) {
                String response_string = EntityUtils.toString(response.getEntity());
                JSONObject json = new JSONObject(response_string);
                contactId = json.getString("id");
                System.out.println("BotKnowledgeBase__c Insertion successful. Status code returned is " + statusCode);
            } else {
                System.out.println("BotKnowledgeBase__c  Insertion unsuccessful. Status code returned is " + statusCode);
            }
        } catch (JSONException e) {
            System.out.println("Issue creating JSON or processing results for BotKnowledgeBase__c insertion.");
            e.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (NullPointerException npe) {
            npe.printStackTrace();
        }
	}
}