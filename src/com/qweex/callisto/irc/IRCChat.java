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
package com.qweex.callisto.irc;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import com.qweex.callisto.Callisto;
import com.qweex.callisto.R;


import jerklib.ConnectionManager;
import jerklib.Profile;
import jerklib.ServerInformation;
import jerklib.Session;
import jerklib.events.*;
import jerklib.events.IRCEvent.Type;
import jerklib.listeners.IRCEventListener;
import jerklib.util.NickServAuthPlugin;


import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView.OnEditorActionListener;
import android.widget.ViewAnimator;


//TODO: URL detection
//FIXME: Glow from scrollview covers up bottom line
//IDEA: Nicklist
//FIXME: fix syslog like chatView
//FIXME: Quit message doesn't work :\
//FIXME: Messages are being repeated for some stupid reason; there is NO formatting on the duplicate messages
//FIXME: If NickServ logs you out because you don't identify, the app will crash. I blame jerklib

public class IRCChat extends Activity implements IRCEventListener
{
	private String SERVER_NAME = "irc.geekshed.net";
	private String CHANNEL_NAME = "#jbgossip"; //"#jupiterbroadcasting";
	private String profileNick;
	private String profilePass;
	private boolean SHOW_TIME = true;
	
	private String CLR_TEXT = "Black",
				   CLR_TOPIC = "DarkGoldenRod",
				   CLR_ME = "DarkViolet",
				   CLR_JOIN = "Blue",
				   CLR_MYNICK = "SeaGreen",
				   CLR_NICK = "Blue",
				   CLR_PART = CLR_JOIN,
				   CLR_QUIT = CLR_JOIN,
				   CLR_KICK = CLR_JOIN,
				   CLR_ERROR = "Maroon",
				   CLR_MENTION = "LightCoral",
				   CLR_PM = "DarkCyan";
	
	
	private static final int LOG_ID=Menu.FIRST+1;
	private static final int LOGOUT_ID=LOG_ID+1;
	
	private static ConnectionManager manager;
	private static Session session;
	private static NotificationManager mNotificationManager;
	private static int mentionCount = 0;
	private static PendingIntent contentIntent;
	private static boolean isFocused = false;
	ScrollView sv, sv2;
	TextView syslog; //chat;
	EditText input;
	Spanned received;
	SimpleDateFormat sdfTime = new SimpleDateFormat("'['HH:mm']'");
	boolean isLandscape;
	
	public static HashMap<String, CharSequence> nickColors = new HashMap<String, CharSequence>();
	public static ArrayList<CharSequence> COLOR_LIST;
	List<String> nickList;
	
	static Handler chatHandler = null;
	Runnable chatUpdater;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		if(chatHandler==null)
		{
			chatHandler = new Handler();
			chatUpdater = new Runnable()
			{
		        @Override
		        public void run()
		        {
		        	if(received.equals(new SpannableString("")))
		        		return;
		            Callisto.chatView.append(received);
		            Linkify.addLinks(Callisto.chatView, Linkify.WEB_URLS);
		            Linkify.addLinks(Callisto.chatView, Linkify.EMAIL_ADDRESSES);
		            received = new SpannableString("");
		            Callisto.chatView.invalidate();
		            sv.fullScroll(ScrollView.FOCUS_DOWN);
		            input.requestFocus();
		        }
			};
		}
		
		isLandscape = getWindowManager().getDefaultDisplay().getWidth() > getWindowManager().getDefaultDisplay().getHeight();
		mNotificationManager =  (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		COLOR_LIST = new ArrayList<CharSequence>(Arrays.asList(Callisto.RESOURCES.getTextArray(R.array.colors)));
		Collections.shuffle(COLOR_LIST);
		
		profileNick = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_nick", null);
		profilePass = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_pass", null);
		if(session!=null)
		{
			resume();
			return;
		}
		
		LinearLayout ll = new LinearLayout(this);
		ll.setBackgroundColor(Callisto.RESOURCES.getColor(R.color.backClr));
		ll.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams params 
			= new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		ll.setLayoutParams(params);
		ll.setId(1337);
		ll.setPadding(getWindowManager().getDefaultDisplay().getHeight()/10,
				  getWindowManager().getDefaultDisplay().getHeight()/(isLandscape?6:4),
				  getWindowManager().getDefaultDisplay().getHeight()/10,
				  0);
		final EditText user = new EditText(this);
		final EditText pass = new EditText(this);
		Button login = new Button(this);
		params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		user.setText(profileNick);
		pass.setText(profilePass);
		user.setLayoutParams(params);
		pass.setLayoutParams(params);
		login.setLayoutParams(params);
		login.setCompoundDrawables(Callisto.RESOURCES.getDrawable(R.drawable.ic_menu_login), null, null, null);
		
		user.setHint("Nick");
		pass.setHint("Password (Optional)");
		pass.setRawInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
		login.setText("Login");
		login.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
		login.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				String nick = user.getText().toString();
				if(nick==null || nick.trim().equals(""))
				{
					Toast.makeText(IRCChat.this, "Dude, you have to enter a nick.", Toast.LENGTH_SHORT).show();
					return;
				}
				String passwd = pass.getText().toString();
				SharedPreferences.Editor e = PreferenceManager.getDefaultSharedPreferences(v.getContext()).edit();
				e.putString("irc_nick", nick);
				e.putString("irc_pass", passwd);
				profileNick = nick;
				e.commit();
				initiate();
			}
		});
		
		ll.addView(user);
		ll.addView(pass);
		ll.addView(login);
		setContentView(ll);
	}
	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		if (keyCode == KeyEvent.KEYCODE_HOME)
		{
			isFocused = false;
		}
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{	
			if(session==null || true)
			{
				finish();
				isFocused = false;
				return true;
			}
			/*
			Intent i = new Intent(IRCChat.this, Callisto.class);
            startActivity(i);
	        return true;
	        */
		}
	    if (keyCode == KeyEvent.KEYCODE_SEARCH)
	    {
	    	String t = input.getText().toString();
	    	int i = input.getSelectionStart();
	    	int i2 = input.getSelectionEnd();
	    	if(i!=i2)
	    		return false;
	    	t = t.substring(0, i);
	    	t = t.substring(t.lastIndexOf(" ")+1);
	    	String s = "";
	    	
	    	Iterator<String> iterator = nickList.iterator();
	    	while(iterator.hasNext())
	    	{
	    		s = (String) iterator.next();
	    		if(s.toUpperCase().equals(t.toUpperCase()))
	    		{
	    			s = (String) iterator.next();
	    			break;
	    		}
	    		if(s.toUpperCase().startsWith(t.toUpperCase()))
	    			break;
	    	}
	    	if(!iterator.hasNext())
	    		s = null;
	    	if(s!=null)
	    	{
	    		String newt = input.getText().toString().substring(0,i-t.length())
	    				+ s
	    				+ input.getText().toString().substring(i);
	    		input.setText(newt);
	    		try {
	    			input.setSelection(i-t.length()+s.length());
	    		} catch(Exception e){}
	    	}
	    	else
	    	{
	    		//TODO: Notify user that no nick was found
	    		/*
	    		input.setBackgroundColor(Callisto.RESOURCES.getColor(R.color.Salmon));
	    		try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
	    		input.setBackgroundDrawable(Callisto.RESOURCES.getDrawable(android.R.drawable.editbox_background));
	    		//*/
	    	}
	    }
	    return super.onKeyDown(keyCode, event);
	} 
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	menu.add(0, LOG_ID, 0, "Log").setIcon(R.drawable.ic_menu_chat_dashboard);
    	menu.add(0, LOGOUT_ID, 0, "Logout").setIcon(R.drawable.ic_menu_close_clear_cancel);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
 
        switch (item.getItemId())
        {
        case LOG_ID:
        	((ViewAnimator) findViewById(R.id.viewanimator)).showNext();
            return true;
        case LOGOUT_ID:
        	logout();
        	return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
      super.onConfigurationChanged(newConfig);
      LinearLayout ll = (LinearLayout) findViewById(1337);
      if(ll!=null)
		ll.setPadding(getWindowManager().getDefaultDisplay().getHeight()/10,
				  getWindowManager().getDefaultDisplay().getHeight()/(isLandscape?6:4),
				  getWindowManager().getDefaultDisplay().getHeight()/10,
				  0);
    }

    public void resume()
    {
    	isFocused = true;
    	mentionCount = 0;
    	nickColors.put(profileNick, CLR_MYNICK);
		COLOR_LIST.remove(CLR_MYNICK);
		setContentView(R.layout.irc);
		sv = (ScrollView) findViewById(R.id.scrollView);
		sv2 = (ScrollView) findViewById(R.id.scrollView2);
		ScrollView test = ((ScrollView)Callisto.chatView.getParent());
		if(test!=null)
			test.removeView(Callisto.chatView);
		sv.addView(Callisto.chatView);
		syslog = (TextView) findViewById(R.id.syslog);
		input = (EditText) findViewById(R.id.inputField);
		input.setOnEditorActionListener(new OnEditorActionListener(){
			@Override
			public boolean onEditorAction(TextView arg0, int arg1, KeyEvent arg2) {
				input.post(sendMessage);
				return false;
			}
		});
		if(session!=null)
			session.addIRCEventListener(this);
		if(Callisto.notification_chat!=null)
			Callisto.notification_chat.setLatestEventInfo(this,  "In the JB Chat",  "No new mentions", contentIntent);
    }
    
	
    public void initiate()
    {
    	findViewById(1337).startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right));
    	resume();
    	SHOW_TIME = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("irc_time", true);
    	
    	//Read colors
    	CLR_TEXT = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_text", "Black");
	    CLR_TOPIC = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_topic", "DarkGoldenRod");
	    CLR_MYNICK = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_mynick", "SeaGreen");
	    CLR_ME = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_me", "DarkViolet");
		CLR_JOIN = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_join", "Blue");
	    CLR_NICK = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_nick", "Blue");
	    CLR_PART = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_part", CLR_JOIN);
	    CLR_QUIT = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_quit", CLR_JOIN);
	    CLR_KICK = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_kick", CLR_JOIN);
		CLR_ERROR = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_error", "Maroon");
		CLR_MENTION = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_mention", "LightCoral");
		CLR_PM = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_color_pm", "DarkCyan");
    	
		
		Intent notificationIntent = new Intent(this, IRCChat.class);
		contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    	Callisto.notification_chat = new Notification(R.drawable.callisto, "Connecting to IRC", System.currentTimeMillis());
		Callisto.notification_chat.flags = Notification.FLAG_ONGOING_EVENT;
       	Callisto.notification_chat.setLatestEventInfo(this,  "In the JB Chat",  "No new mentions", contentIntent);
       	mNotificationManager.notify(Callisto.NOTIFICATION_ID, Callisto.notification_chat);
       	
       	
		manager = new ConnectionManager(new Profile(profileNick));
		session = manager.requestConnection(SERVER_NAME);
		if(profilePass!=null && profilePass!="")
		{
			final NickServAuthPlugin auth = new NickServAuthPlugin(profilePass, 'e', session, Arrays.asList(CHANNEL_NAME));
			session.onEvent(auth, Type.CONNECT_COMPLETE , Type.MODE_EVENT);
		}
		session.addIRCEventListener(this);
		
		session.setInternalParser(new jerklib.parsers.DefaultInternalEventParser()
		{
			@Override
			public IRCEvent receiveEvent(IRCEvent e)
			{
				//This part isn't needed but I am keeping it in because my tiki doll told me to
				try
				{
				String action = e.getRawEventData();
				action = action.substring(action.lastIndexOf(":")+1);
					try {
						if(action.startsWith("ACTION"))
						{
							action = action.substring("ACTION".length(), action.length()-1);
							String person = e.getRawEventData().substring(1, action.indexOf("!"));
						}
					}catch(Exception ex){}
				}
				catch(Exception ex){}
				return super.receiveEvent(e);
			}
		});
		//*/
    }
    
    public void logout()
    {
    	String q = PreferenceManager.getDefaultSharedPreferences(this).getString("irc_quit", null);
    	if(q==null)
    		manager.quit();
    	else
    		manager.quit(q);
		manager = null;
		session = null;
		mNotificationManager.cancel(Callisto.NOTIFICATION_ID);
		isFocused = false;
		Callisto.chatView.setText("");
		finish();
    }
    
  //INVITE_EVENT
  //NUMERIC_ERROR_EVENT
  //UnresolvedHostnameErrorEvent
    
	/*
	 *what
	 * WATCH???
	 * HELPOP???
	 * SETNAME???
	 * VHOST???
	 * MODE???
	 * 
	 *mod
	 * INVITE
	 * KICK
	 * http://www.geekshed.net/commands/ircop/
	 */
	public void receiveEvent(IRCEvent e)
	{
		Log.d("IRCCHat:receiveEvent", e.getRawEventData());
		Log.d("IRCCHat:receiveEvent", "---" + e.getType());
		switch(e.getType())
		{
		//Misc events
			case NICK_LIST_EVENT:
				nickList = ((NickListEvent) e).getNicks();
				Collections.sort(nickList, String.CASE_INSENSITIVE_ORDER);
				break;
				//TODO: This might not work. I dunno.
			case CTCP_EVENT:
				CtcpEvent ce = (CtcpEvent) e;
				String realEvent = ce.getCtcpString().substring(0, ce.getCtcpString().indexOf(" "));
				if(realEvent.equals("ACTION"))
				{
					String realAction = ce.getCtcpString().substring(realEvent.length()).trim();
					String realPerson = ce.getRawEventData().substring(1, ce.getRawEventData().indexOf("!"));
					received = getReceived("* " + realPerson + " " + realAction, null, CLR_ME);
					chatHandler.post(chatUpdater);
				}
				break;
			case AWAY_EVENT://This isn't even effing used! (for other people's away
				AwayEvent a = (AwayEvent) e;
				if(a.isYou())
				{
					received = getReceived("You are " + (a.isAway() ? " now " : " no longer ") + "away (" + a.getAwayMessage() + ")", null, CLR_TOPIC);					
				}
				received = getReceived("[AWAY]", a.getNick() + " is away: " + a.getAwayMessage(), CLR_TOPIC);
				chatHandler.post(chatUpdater);
				break;
				
		//Syslog events
			case SERVER_INFORMATION:
				//FORMAT
				ServerInformationEvent s = (ServerInformationEvent) e;
				ServerInformation S = s.getServerInformation();
				received = getReceived("[INFO]", S.getServerName(), CLR_TOPIC);
				chatHandler.post(logUpdater);
				break;
			case SERVER_VERSION_EVENT:
				ServerVersionEvent sv = (ServerVersionEvent) e;
				received = getReceived("[VERSION]", sv.getVersion(), CLR_TOPIC);
				chatHandler.post(logUpdater);
				break;
			case CONNECT_COMPLETE:
				ConnectionCompleteEvent c = (ConnectionCompleteEvent) e;
				received = getReceived(null, c.getActualHostName() + "\nConnection complete", CLR_TOPIC);
				e.getSession().join(CHANNEL_NAME);
				chatHandler.post(logUpdater);
				break;
			case JOIN_COMPLETE:
				//JoinCompleteEvent jce = (JoinCompleteEvent) e;
				received = getReceived("[JOIN]", "Join complete, you are now orbiting Jupiter Broadcasting!", CLR_TOPIC);
				chatHandler.post(chatUpdater);
				break;
			case MOTD:
				MotdEvent mo = (MotdEvent) e;
				received = getReceived("[MOTD]", mo.getMotdLine(), CLR_TOPIC);
				chatHandler.post(logUpdater);
				break;
			case NOTICE:
				NoticeEvent ne = (NoticeEvent) e;
				if((ne.byWho()!=null && ne.byWho().equals("NickServ")) || e.getRawEventData().startsWith(":NickServ"))
				{
					received = getReceived("[NICKSERV]", ne.getNoticeMessage(), CLR_TOPIC);
					chatHandler.post(chatUpdater);
				}
				else
				{
					received = getReceived("[NOTICE]", ne.getNoticeMessage(), CLR_TOPIC);
					chatHandler.post(logUpdater);
				}
				break;
			
		//Chat events
			case TOPIC:
				TopicEvent t = (TopicEvent) e;
				received = getReceived(t.getTopic() +  " (set by " + t.getSetBy() + " on " + t.getSetWhen() + " )", null, CLR_TOPIC);
				chatHandler.post(chatUpdater);
				break;
			
			case PRIVATE_MESSAGE:
			case CHANNEL_MESSAGE:
				MessageEvent m = (MessageEvent) e;
				if((e.getType()).equals(jerklib.events.IRCEvent.Type.PRIVATE_MESSAGE))
					received = getReceived("->" + m.getNick(), m.getMessage(), CLR_PM);
				else
					received = getReceived(m.getNick(), m.getMessage(), null);
				chatHandler.post(chatUpdater);
				break;
			case JOIN:
				JoinEvent j = (JoinEvent) e;
				nickList.add(j.getNick());
				received = getReceived(j.getNick() + " entered the room.", null, CLR_JOIN);
				chatHandler.post(chatUpdater);
				break;
			case NICK_CHANGE:
				NickChangeEvent ni = (NickChangeEvent) e;
				received = getReceived(ni.getOldNick() + " changed their nick to " + ni.getNewNick(), null, CLR_NICK);
				chatHandler.post(chatUpdater);
				break;
			case PART:
				PartEvent p = (PartEvent) e;
				COLOR_LIST.add(nickColors.get(p.getWho()));
				nickColors.remove(p.getWho());
				nickList.remove(p.getWho());
				received = getReceived("PART: " + p.getWho() + " (" + p.getPartMessage() + ")", null, CLR_PART);
				chatHandler.post(chatUpdater);
				break;
			case QUIT:
				QuitEvent q = (QuitEvent) e;
				COLOR_LIST.add(nickColors.get(q.getNick()));
				nickColors.remove(q.getNick());
				nickList.remove(q.getNick());
				received = getReceived("QUIT:  " + q.getNick() + " (" + q.getQuitMessage() + ")", null, CLR_QUIT);
				chatHandler.post(chatUpdater);
				break;
			case KICK_EVENT:
				KickEvent k = (KickEvent) e;
				received = getReceived("KICK:  " + k.getWho() + " was kicked by " + k.byWho()  + ". (" + k.getMessage() + ")", null, CLR_KICK);
				chatHandler.post(chatUpdater);
				break;
			case NICK_IN_USE:
				NickInUseEvent n = (NickInUseEvent) e;
				received = getReceived("NICKINUSE:  " + n.getInUseNick() + " is in use.", null, CLR_ERROR);
				chatHandler.post(chatUpdater);
				break;
			case WHO_EVENT:
				WhoEvent we = (WhoEvent) e;
				received = getReceived("[WHO]", we.getNick() + " is " + we.getUserName() + "@" + we.getServerName() + " (" + we.getRealName() + ")", CLR_TOPIC);
				chatHandler.post(chatUpdater);
				break;
			case WHOIS_EVENT:
				WhoisEvent wie = (WhoisEvent) e;
				String var = "";
				for(String event : wie.getChannelNames())
					var = var + " " + event;
				received = (Spanned) TextUtils.concat(getReceived("[WHOIS]", wie.getUser() + " is " + wie.getHost() + "@" + wie.whoisServer() + " (" + wie.getRealName() + ")", CLR_TOPIC)
						, getReceived("[WHOIS]", wie.getUser() + " is a user on channels: " + var, CLR_TOPIC)
						, getReceived("[WHOIS]", wie.getUser() + " has been idle for " + wie.secondsIdle() + " seconds", CLR_TOPIC)
						, getReceived("[WHOIS]", wie.getUser() + " has been online since " + wie.signOnTime(), CLR_TOPIC));
				chatHandler.post(chatUpdater);
				break;
			case WHOWAS_EVENT: //TODO: Fix?
				WhowasEvent wwe = (WhowasEvent) e;
				received = getReceived("[WHO]", wwe.getNick() + " is " + wwe.getUserName() + "@" + wwe.getHostName() + " (" + wwe.getRealName() + ")", CLR_TOPIC);
				break;
			
			//Errors that display in both
			case CONNECTION_LOST:
				//ConnectionLostEvent co = (ConnectionLostEvent) e;
				received = getReceived("CONNECTION WAS LOST", null, CLR_ERROR);
				chatHandler.post(chatUpdater);
				chatHandler.post(logUpdater);
				break;
			case ERROR:
				//ErrorEvent ev = (ErrorEvent) e;
				String rrealmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
				received = getReceived("[ERROR OCCURRED]", rrealmsg, CLR_ERROR);
				chatHandler.post(chatUpdater);
				chatHandler.post(logUpdater);
				break;
				
			//Events not handled by jerklib
			case DEFAULT:		//ping
				String realType = e.getRawEventData();
				String realmsg = "";
				if(realType.startsWith("PING"))
					realType = "PING";
				else
					realType = realType.substring(realType.indexOf(" ")+1, realType.indexOf(" ", realType.indexOf(" ")+1));
				int i=0;
				try {
					i = Integer.parseInt(realType);
				} catch(Exception asdf) {}
				
				Log.d("IRCChat:receiveEvent:DEFAULT", realType);
				//PING     //TEST
				if(realType.equals("PING"))
				{
					return;
				}
				//ISON
				if(realType.equals("303"))
				{
					String name = e.getRawEventData().substring(e.getRawEventData().lastIndexOf(":")+1);
					if(name.trim().equals(""))
						return;
					received = getReceived("[ISON]", name + " is online", CLR_TOPIC);
					chatHandler.post(chatUpdater);
					return;
				}
				//MAP
				if(realType.equals("006"))
				{
					realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					received = getReceived("[MAP] " + realmsg, null, CLR_TOPIC);
					chatHandler.post(logUpdater);
					return;
				}
				//LUSERS
				if((i>=250 && i<=255) || i==265 || i==266)
				{
					realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					received = getReceived("[LUSERS]", realmsg, CLR_TOPIC);
					chatHandler.post(logUpdater);
					return;
				}
				/*
				//VERSION //CLEAN: Is this even needed?
				if(realType.equals("351"))
				{
					String realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					received = getReceived("[VERSION] " + realmsg, null, CLR_TOPIC);
					chatHandler.post(logUpdater);
					return;
				}
				*/
				//RULES
				if(realType.equals("232") || realType.equals("309"))
				{
					realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					received = getReceived("[RULES]", realmsg, CLR_TOPIC);
					chatHandler.post(logUpdater);
					return;
				}
				//LINKS
				else if(realType.equals("364") || realType.equals("365"))
				{
					realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					received = getReceived("[LINKS]", realmsg, CLR_TOPIC);
					chatHandler.post(logUpdater);
					return;
				}
				//ADMIN
				else if(i>=256 && i<=259)
				{
					realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					received = getReceived("[ADMIN]", realmsg, CLR_TOPIC);
					chatHandler.post(chatUpdater);
					return;
				}
				//WHO part 2
				else if(realType.equals("315"))
				{
					realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					received = getReceived("[WHO]", realmsg, CLR_TOPIC);
					chatHandler.post(chatUpdater);
				}
				//WHOIS part 2
				else if(realType.equals("307"))
				{
					int ijk = e.getRawEventData().lastIndexOf(" ", e.getRawEventData().indexOf(":",2)-2)+1;
					realmsg = e.getRawEventData().substring(ijk, e.getRawEventData().indexOf(":", 2)-1)
							+ " " + e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					received = getReceived("[WHOIS]", realmsg, CLR_TOPIC);
					chatHandler.post(chatUpdater);
				}
				//USERHOST
				else if(realType.equals("302"))
				{
					realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					realmsg.replaceFirst(Pattern.quote("=+"), " is ");	//TODO: Not working? eh?
					received = getReceived("[USERHOST]", realmsg, CLR_TOPIC);
					chatHandler.post(chatUpdater);
				}
				//CREDITS
				else if(realType.equals("371"))
				{
					realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					realmsg.replaceFirst(Pattern.quote("=+"), " is ");	//TODO: Not working? eh?
					received = getReceived("[CREDITS]", realmsg, CLR_TOPIC);
					chatHandler.post(logUpdater);
				}
				//TIME
				else if(realType.equals("391"))
				{
					realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					received = getReceived("[TIME]", realmsg, CLR_TOPIC);
					chatHandler.post(chatUpdater);
				}
				//USERIP
				else if(realType.equals("340"))
				{
					realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					received = getReceived("[TIME]", realmsg, CLR_TOPIC);
					chatHandler.post(chatUpdater);
				}
				//etc
				else
				{
					realmsg = e.getRawEventData().substring(e.getRawEventData().indexOf(":", 2)+1);
					received = getReceived("[" + realType + "]", realmsg, CLR_TOPIC);
					chatHandler.post(logUpdater);
				}
				break;
				
				
			default:
				break;
			
		}
	}
	

	
	private Spanned getReceived (String theTitle, String theMessage, String specialColor)
	{
		int titleColor = 0xFF000000;
		int msgColor = 0xFF000000;
		try {
		 titleColor+= Callisto.RESOURCES.getColor(
				Callisto.RESOURCES.getIdentifier(
				specialColor!=null ? specialColor :	getNickColor(theTitle), "color", "com.qweex.callisto"));
		 if(theMessage!=null)
			 msgColor+= Callisto.RESOURCES.getColor(
				Callisto.RESOURCES.getIdentifier(theMessage.contains(session.getNick()) ? CLR_MENTION : CLR_TEXT, "color", "com.qweex.callisto"));
		} catch(NullPointerException e) {
		}
		if(theMessage!=null && theMessage.contains(session.getNick()) && !isFocused)
		{
			if(Callisto.notification_chat==null)
				Callisto.notification_chat = new Notification(R.drawable.callisto, "Connecting to IRC", System.currentTimeMillis());
			Callisto.notification_chat.setLatestEventInfo(getApplicationContext(), "In the JB Chat",  ++mentionCount + " new mentions", contentIntent);
			mNotificationManager.notify(Callisto.NOTIFICATION_ID, Callisto.notification_chat);
			if(mentionCount==1)//TODO: Fix the notification to be sent for the first mention
			{
				mNotificationManager.notify(Callisto.NOTIFICATION_ID-1, new Notification(R.drawable.callisto, "New mentions!", System.currentTimeMillis()));
				mNotificationManager.cancel(Callisto.NOTIFICATION_ID-1);
			}
		}
		SpannableString tit = new SpannableString(theTitle==null ? "" : theTitle);
		SpannableString mes = new SpannableString(theMessage==null ? "" : theMessage);
		try {
			//mes = (SpannableString) URLInString(theMessage);
			if(theTitle!=null)
			{
				if(theMessage!=null)
					tit = new SpannableString(tit + ": ");
				tit.setSpan(new ForegroundColorSpan(titleColor), 0, tit.length(), 0);
				tit.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, tit.length(), 0);
			}
			if(theMessage!=null)
			{
				mes.setSpan(new ForegroundColorSpan(msgColor), 0, mes.length(), 0);
			}
		} catch(Exception ieieieie) {
		}
		
		return (Spanned) TextUtils.concat(Html.fromHtml((SHOW_TIME ? ("<small>" + sdfTime.format(new Date()) + "</small> ") : ""))
									 , tit, mes, "\n");
	}
	
	
	public String getNickColor(String nickInQ)
	{
		if(!nickColors.containsKey(nickInQ))
		{
			nickColors.put(nickInQ, COLOR_LIST.get(0));
			COLOR_LIST.remove(0);
		}
		return (String) nickColors.get(nickInQ);
	}

	
	Runnable sendMessage = new Runnable(){
		@Override
        public void run() {
			String newMessage = input.getText().toString();
			if(newMessage=="")
				return;
			SpannableString st = new SpannableString(session.getNick());
			int colorBro = 0xFF000000 +  
					Callisto.RESOURCES.getColor(
					Callisto.RESOURCES.getIdentifier(CLR_ME, "color", "com.qweex.callisto"));
			int colorBro2 = 0xFF000000 +  
					Callisto.RESOURCES.getColor(
					Callisto.RESOURCES.getIdentifier(CLR_TEXT, "color", "com.qweex.callisto"));
			
			SpannableString st2 = new SpannableString(newMessage);
			try {
			st.setSpan(new ForegroundColorSpan(colorBro), 0, session.getNick().length(), 0);
			st.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, session.getNick().length(), 0);
			st2.setSpan(new ForegroundColorSpan(colorBro2), 0, newMessage.length(), 0);
			}
			catch(Exception e) {}
			
			
			
			if(parseOutgoing(newMessage))
			{
				Spanned x = (Spanned) TextUtils.concat(
						Html.fromHtml((SHOW_TIME ? ("<small>" + sdfTime.format(new Date()) + "</small> ") : "")),
						st,
						": ",
						st2
						//,"\n" //FIXME: needed?
						);
				Callisto.chatView.append(x); //HERE
			}
			input.requestFocus();
			input.setText("");
		}
	};
	
	
	//Boolean return value tells if the output should be appended to the chat log
	private boolean parseOutgoing(String msg)
	{
		msg = msg.replace("\n", "");
		if(!msg.startsWith("/"))
		{
			session.getChannel(CHANNEL_NAME).say(msg);
			return true;
		}
		if(msg.toUpperCase().startsWith("/NICK "))
		{
			msg =  msg.substring("/NICK ".length());
			session.changeNick(msg);
			return false;
		}
		if(msg.toUpperCase().startsWith("/QUIT ") || msg.trim().toUpperCase().startsWith("/PART"))
		{
			logout();
			return false;
		}
		if(msg.toUpperCase().startsWith("/WHO "))
		{
			session.who(msg.substring("/WHO ".length()));
			return false;
		}
		if(msg.toUpperCase().startsWith("/WHOIS "))
		{
			session.whois(msg.substring("/WHOIS ".length()));
			return false;
		}
		if(msg.toUpperCase().startsWith("/WHOWAS "))
		{
			session.whoWas(msg.substring("/WHOWAS ".length()));
			return false;
		}
		if(msg.toUpperCase().startsWith("/MSG "))
		{
			String targetNick = msg.substring("/MSG ".length(), msg.indexOf(" ", "/MSG ".length()+1));
			String targetMsg = msg.substring("/MSG ".length() + targetNick.length()); 
			session.sayPrivate(targetNick, targetMsg);
			received = getReceived("<-" + targetNick, targetMsg, CLR_PM);
			chatHandler.post(chatUpdater);
			return false;
		}
		if(msg.toUpperCase().startsWith("/ISON ")
		|| msg.toUpperCase().equals("/MOTD")
		|| msg.toUpperCase().equals("/RULES")
		|| msg.toUpperCase().equals("/LUSERS")
		|| msg.toUpperCase().startsWith("/MAP")
		|| msg.toUpperCase().startsWith("/VERSION")
		|| msg.toUpperCase().startsWith("/LINKS")
		|| msg.toUpperCase().startsWith("/IDENTIFY ")
		|| msg.toUpperCase().startsWith("/ADMIN")
		|| msg.toUpperCase().startsWith("/USERHOST")
		|| msg.toUpperCase().startsWith("/TOPIC ")
		|| msg.toUpperCase().startsWith("/CREDITS")
		|| msg.toUpperCase().startsWith("/TIME")
		|| msg.toUpperCase().startsWith("/DNS")		//Denied
		|| msg.toUpperCase().startsWith("/USERIP ")
		|| msg.toUpperCase().startsWith("/STATS ")	//Denied
		|| msg.toUpperCase().startsWith("/MODULE")	//Posts as "Notice", not "Module". I am ok with this.
		)
		{
			session.sayRaw(msg.substring(1));
			return false;
		}
		if(msg.toUpperCase().startsWith("/ME "))
		{
			session.action(CHANNEL_NAME, "ACTION" + msg.substring(3));
			received = getReceived("* " + session.getNick() + msg.substring(3), null, CLR_ME);
			chatHandler.post(chatUpdater);
			return false;
		}
		
		if(msg.toUpperCase().startsWith("/PING ")) //TODO: I have no clue if this works right.
		{
			session.action(CHANNEL_NAME, "PING" + msg.substring("/PING".length()));
			//session.ctcp(msg.substring("/PING ".length()), "ping");
			return true;
		}
		if(msg.toUpperCase().startsWith("/AWAY "))
		{
			session.setAway(msg.toUpperCase().substring("/AWAY ".length()));
			return false;	//TODO: CHECK
		}
		if(msg.toUpperCase().equals("/AWAY"))
		{
			if(session.isAway())
				session.unsetAway();
			else
				session.setAway("Gone away for now");
			return false;
		}
		
		if(msg.toUpperCase().startsWith("/JOIN ")
	    || msg.toUpperCase().startsWith("/CYCLE ")
	    || msg.toUpperCase().startsWith("/LIST ")
	    || msg.toUpperCase().startsWith("/KNOCK "))
		{
			Toast.makeText(IRCChat.this, "What, is the JB chat not enough for you?!", Toast.LENGTH_SHORT).show();
			return false;
		}
		/*
		if(msg.toUpperCase().startsWith("/MOTD ")
		|| msg.toUpperCase().startsWith("/LUSERS "))
		{
			Toast.makeText(IRCChat.this, "Stop that, you're trying to break stuff", Toast.LENGTH_SHORT).show();
			return false;
		}
		*/
		received = getReceived("[CALLISTO]", "Command not recognized!", CLR_TOPIC);
		chatHandler.post(chatUpdater);
		return false;
	}
	
  
	Runnable logUpdater = new Runnable()
	{
        @Override
        public void run()
        {
            syslog.append(received);
            syslog.invalidate();
            sv2.fullScroll(ScrollView.FOCUS_DOWN);
        };	

    };

    // Replaces URLs with html hrefs codes
    public SpannableString URLInString(String input)
    {
    	SpannableString output = new SpannableString("");
    	if(input==null)
    		return output;
    	output = new SpannableString(input);
    	String [] parts = input.split("\\s");
    	int lastIndex = 0;
    	for( String item : parts )
    	{
			try {
	            URL url = new URL(item);
	            
	            System.out.println("URL: " + item);
	            output.setSpan(new URLSpan(item), lastIndex, item.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	        } catch (MalformedURLException e) {
	        }
			lastIndex+=item.length();
    	}
    	return output;
    }

}
