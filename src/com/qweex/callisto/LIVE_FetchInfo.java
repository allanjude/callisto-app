package com.qweex.callisto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

/** Updates the current and next track information. */
public class LIVE_FetchInfo extends AsyncTask<Void, Void, Void>
{
	private static final String infoURL = "http://jbradio.airtime.pro/api/live-info";
	private static Matcher liveMatcher = null;
	
	@Override
    protected Void doInBackground(Void... c)
	{
    HttpClient httpClient = new DefaultHttpClient();
    HttpContext localContext = new BasicHttpContext();
    HttpGet httpGet = new HttpGet(infoURL);
    HttpResponse response;
    try
	{
		response = httpClient.execute(httpGet, localContext);
	    BufferedReader reader = new BufferedReader(
		        new InputStreamReader(
		          response.getEntity().getContent()
		        )
		      );
	    
	    String line = null, result = "";
	    while ((line = reader.readLine()) != null){
	      result += line + "\n";
	    }
	    
	    //if(liveMatcher==null)
	    	liveMatcher = (Pattern.compile(".*?\"currentShow\".*?"
	    					+ "\"name\":\"(.*?)\""
	    					+ ".*"
							+ "\"name\":\"(.*?)\""
	    					+ ".*?")
	    					).matcher(result);
	    if(liveMatcher.find())
	    	Callisto.playerInfo.title = liveMatcher.group(1);
	    if(liveMatcher.groupCount()>1)
	    	Callisto.playerInfo.show = liveMatcher.group(2);
	    
	    Callisto.updateHandler.sendEmptyMessage(0);
	    //CallistoWidget.updateAllWidgets(Callisto.LIVE_PreparedListener.c);
	    
	} catch (ClientProtocolException e) {
		// TODO EXCEPTION
		e.printStackTrace();
	} catch (IOException e) {
		e.printStackTrace();
	} catch (IllegalStateException e) {
		e.printStackTrace();
	};
    
    
    Intent notificationIntent = new Intent(Callisto.LIVE_PreparedListener.c, Callisto.class);
	PendingIntent contentIntent = PendingIntent.getActivity(Callisto.LIVE_PreparedListener.c, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
	if(Callisto.notification_playing==null)
	{
		Callisto.notification_playing = new Notification(R.drawable.callisto, null, System.currentTimeMillis());
		Callisto.notification_playing.flags = Notification.FLAG_ONGOING_EVENT;
	}
	Callisto.notification_playing.setLatestEventInfo(Callisto.LIVE_PreparedListener.c, Callisto.playerInfo.title,  "JB Radio", contentIntent);
   	NotificationManager mNotificationManager =  (NotificationManager) Callisto.LIVE_PreparedListener.c.getSystemService(Context.NOTIFICATION_SERVICE);
   	mNotificationManager.notify(Callisto.NOTIFICATION_ID, Callisto.notification_playing);
	//*/
    
    return null;
	}
}