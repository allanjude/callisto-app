/*
Copyright (C) 2012 Qweex
This file is a part of Callisto.

Callisto is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

Callisto is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Callisto; If not, see <http://www.gnu.org/licenses/>.
*/
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

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;

//FEATURE: All of it

public class NowPlaying extends Activity
{
	private final String infoURL = "http://jbradio.airtime.pro/api/live-info";
	private final static String errorReportURL = "http://software.qweex.com/error_report.php";
	Matcher m = null;
    String currentShow = "Unknown", nextShow = "Unknown";
    TextView current, next;
    ImageButton playPause;
    String live_url;
    static ProgressDialog pd;
    static Dialog dg;
    ImageButton bigButton;
    
    private final int JBLIVE_MENU_ID = Menu.FIRST;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.nowplaying);
		
		dg = new Dialog(this);
		bigButton = (ImageButton) findViewById(R.id.playPause);
		TextView t = new TextView(this);
		t.setText("An error occurred. This may be a one time thing, or your device does not support the stream. You can try going to JBlive.info (via the Menu button) to see if it's just this app.");
		dg.setContentView(t);
		dg.setTitle("By the beard of Zeus!");
		
		
		current = (TextView) findViewById(R.id.current);
		next = (TextView) findViewById(R.id.next);
		playPause = (ImageButton) findViewById(R.id.playPause);
		playPause.setOnClickListener(playButton);
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	super.onCreateOptionsMenu(menu);
    	menu.add(Menu.NONE, JBLIVE_MENU_ID, Menu.NONE, "JBLive");
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	switch (item.getItemId())
    	{
    	case JBLIVE_MENU_ID:
    	    Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://jblive.info"));
    	    startActivity(myIntent);
    		return true;
    	}
    	return super.onOptionsItemSelected(item);
    }
	
	public void update()
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
		    
		    m = (Pattern.compile(".*?\"currentShow\".*?"
		    					+ "\"name\":\"(.*?)\""
		    					+ ".*"
								+ "\"name\":\"(.*?)\""
		    					+ ".*?")
		    					).matcher(result);
		    if(m.find())
		    	currentShow = m.group(1);
		    if(m.groupCount()>1)
		    	nextShow = m.group(2);
	    	
		    current.setText(currentShow);
		    Callisto.playerInfo.title = currentShow;
		    Callisto.playerInfo.show = "JB Radio";
		    next.setText(nextShow);
		    
		} catch (ClientProtocolException e) {
			// TODO EXCEPTION
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	OnClickListener playButton = new OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			if(!Callisto.playerInfo.isPaused)
			{
				Callisto.mplayer.pause();
				Callisto.playerInfo.isPaused = true;
			}
			
			if(Callisto.live_player == null || !Callisto.live_isPlaying)
			{
				
				
				liveInit();
				Callisto.live_player.setOnPreparedListener(livePreparedListener);
					try {
				Callisto.live_player.setDataSource(live_url);
				livePrepare(NowPlaying.this);
				} catch (Exception e) {
					dg.show();
					sendErrorReport("EXCEPTION");
					e.printStackTrace();
				}
			}
			else
			{
				if(Callisto.live_isPlaying)
				{
					bigButton.setImageDrawable(Callisto.RESOURCES.getDrawable(R.drawable.ic_media_play_lg));
					Callisto.live_player.pause();
				}
				else
				{
					bigButton.setImageDrawable(Callisto.RESOURCES.getDrawable(R.drawable.ic_media_pause_lg));
					Callisto.live_player.start();
				}
				Callisto.live_isPlaying = !Callisto.live_isPlaying;
			}
		}
	};
	
	static public void liveInit()
	{
		Callisto.live_player = new MediaPlayer();
		Callisto.live_player.setAudioStreamType(AudioManager.STREAM_MUSIC);
		Callisto.live_player.setOnErrorListener(new OnErrorListener() {
		    public boolean onError(MediaPlayer mp, int what, int extra) {
		    	dg.show();
		    	String whatWhat="";
		    	switch (what) {
		        case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
		            whatWhat = "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK";
		            break;
		        case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
		        	whatWhat = "MEDIA_ERROR_SERVER_DIED";
		            break;
		        case MediaPlayer.MEDIA_ERROR_UNKNOWN:
		        	whatWhat = "MEDIA_ERROR_UNKNOWN";
		            break;
		        default:
		        	whatWhat = "???";
		        }

		    	NowPlaying.sendErrorReport(whatWhat);
		        return true;
		    }
		});
	}
	
	static public void livePrepare(Context c)
	{
		pd = ProgressDialog.show(c, "Buffering", Callisto.RESOURCES.getString(R.string.loading_msg), true, false);
		Callisto.live_player.prepareAsync();
	}
	
	
	OnPreparedListener livePreparedListener = new OnPreparedListener()
	{
		@Override
		public void onPrepared(MediaPlayer arg0) {
			pd.cancel();
			try {
				Callisto.live_player.start();
				update();
				Callisto.live_isPlaying = true;
				bigButton.setImageDrawable(Callisto.RESOURCES.getDrawable(R.drawable.ic_media_pause_lg));
			}
			catch(Exception e)
			{
				dg.show();
				e.printStackTrace();
			}
		}
	};
	
	static OnPreparedListener livePreparedListenerOther = new OnPreparedListener()
	{
		@Override
		public void onPrepared(MediaPlayer arg0) {
			pd.cancel();
			try {
				Callisto.live_player.start();
				Callisto.live_isPlaying = true;
			}
			catch(Exception e)
			{
				NowPlaying.dg.show();
			}
		}
	};
	
	@Override
	public void onResume()
	{
		super.onResume();
		live_url = PreferenceManager.getDefaultSharedPreferences(NowPlaying.this).getString("live_url", "http://jbradio.out.airtime.pro:8000/jbradio_b");
		
		if(Callisto.live_player!=null)
			update();
		if(Callisto.live_isPlaying)
			bigButton.setImageDrawable(Callisto.RESOURCES.getDrawable(R.drawable.ic_media_pause_lg));
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}
	
	public static void sendErrorReport(String msg)
	{
		String errorReport = NowPlaying.errorReportURL + "?id=Callisto&v=" + Callisto.appVersion + "&err=" + android.os.Build.VERSION.RELEASE + "_" + msg;
		
		
		HttpClient httpClient = new DefaultHttpClient();
	    HttpContext localContext = new BasicHttpContext();
	    HttpGet httpGet = new HttpGet(errorReport);
	    try {
	    httpClient.execute(httpGet, localContext);
	    }catch(Exception e){}
	}
}
