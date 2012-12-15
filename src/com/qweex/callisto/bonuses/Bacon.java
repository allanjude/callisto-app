package com.qweex.callisto.bonuses;

import java.io.File;

import com.qweex.callisto.Callisto;

import android.app.Activity;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.WebView;

public class Bacon extends Activity {
	MediaPlayer player;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setTitle("Oppa Bacon Style!");
		WebView w = new WebView(this);
		System.out.println("file:///mnt/sdcard/" + Callisto.storage_path + "/extras/");
		w.loadDataWithBaseURL("file:///mnt/sdcard/" + Callisto.storage_path + "/extras/", "<img style='width:100%' src='gangnam.gif' /><br/><img style='width:100%' src='baconlove.gif' />", "text/html", "utf-8", null);
		setContentView(w);
		
		try {
		player = new MediaPlayer();
		File target = new File(Environment.getExternalStorageDirectory(), Callisto.storage_path + File.separator + "extras" + File.separator + "gangnam.mid");
		player.setDataSource(target.getAbsolutePath());
        player.prepare();
        player.start();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
      super.onConfigurationChanged(newConfig);
    }
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if(this.isFinishing())
			player.reset();
	}
}