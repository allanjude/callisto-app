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
package com.qweex.callisto.podcast;

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Button;
import com.qweex.callisto.Callisto;
import com.qweex.callisto.R;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.ParseException;

/** A class to start downloading a file outside the UI thread. */
public class DownloadTask extends AsyncTask<String, Object, Boolean>
{

    private String Title, Date, Link, Show;
    private long TotalSize;
    private File Target;
    private final int NOTIFICATION_ID = 3696;
    private final int TIMEOUT_CONNECTION = 5000;
    private final int TIMEOUT_SOCKET = 30000;
    private NotificationManager mNotificationManager;
    private PendingIntent contentIntent;
    public static boolean running = false;
    public Context context;
    private int outer_failures = 0, failed = 0, inner_failures;
    private final int INNER_LIMIT=5, OUTER_LIMIT=10;

    public DownloadTask(Context c)
    {
        super();
        context = c;
    }

    @Override
    protected void onPreExecute()
    {
        running = true;
        Log.i("EpisodeDesc:DownloadTask", "Beginning downloads");
        Intent notificationIntent = new Intent(context, DownloadList.class);
        contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
        Callisto.notification_download = new Notification(R.drawable.ic_action_download, Callisto.RESOURCES.getString(R.string.beginning_download), System.currentTimeMillis());
        Callisto.notification_download.flags = Notification.FLAG_ONGOING_EVENT;
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Callisto.notification_download.setLatestEventInfo(context.getApplicationContext(), Callisto.RESOURCES.getString(R.string.downloading) + " " + Callisto.current_download + " " +  Callisto.RESOURCES.getString(R.string.of) + " " + Callisto.downloading_count + ": 0%", Show + ": " + Title, contentIntent);
    }


    @Override
    protected Boolean doInBackground(String... params)
    {

        boolean isVideo;
        Cursor current;

        Callisto.notification_download.setLatestEventInfo(context.getApplicationContext(),
                Callisto.RESOURCES.getString(R.string.downloading) + "...",
                "", contentIntent);
        mNotificationManager.notify(NOTIFICATION_ID, Callisto.notification_download);

        long id = 0;
        Log.e("YO DAWG:", "Preparing to do sum dlinng");
        while(DownloadList.getDownloadCount(context, DownloadList.ACTIVE)>0)
        {

            if(isCancelled())
            {
                mNotificationManager.cancel(NOTIFICATION_ID);
                return false;
            }
            try
            {
                String dlList = PreferenceManager.getDefaultSharedPreferences(context).getString("ActiveDownloads", "");
                Log.e("YO DAWG:", "DL LIST: " + dlList);
                //id = Long.parseLong(dlList.substring(1, dlList.indexOf('|', 1)));

                id = DownloadList.getDownloadAt(context, DownloadList.ACTIVE, 0);
                if(id<=0)
                {
                    isVideo=true;
                    current = Callisto.databaseConnector.getOneEpisode(id*-1);
                }
                else
                {
                    isVideo=false;
                    current = Callisto.databaseConnector.getOneEpisode(id);
                }
                current.moveToFirst();
                Link = current.getString(current.getColumnIndex(isVideo ? "vidlink" : "mp3link"));
                Title = current.getString(current.getColumnIndex("title"));
                Date = current.getString(current.getColumnIndex("date"));
                Show = current.getString(current.getColumnIndex("show"));
                Log.i("EpisodeDesc:DownloadTask", "Starting download: " + Link);
                Date = Callisto.sdfFile.format(Callisto.sdfRaw.parse(Date));


                Target = new File(Environment.getExternalStorageDirectory(), Callisto.storage_path + File.separator + Show);
                Target.mkdirs();
                if(Title.indexOf("|")>0)
                    Title = Title.substring(0, Title.indexOf("|"));
                Title=Title.trim();
                Target = new File(Target, Date + "__" + DownloadList.makeFileFriendly(Title) + EpisodeDesc.getExtension(Link));

                Log.i("EpisodeDesc:DownloadTask", "Path: " + Target.getPath());
                URL url = new URL(Link);
                Log.i("EpisodeDesc:DownloadTask", "Opening the connection...");
                HttpURLConnection ucon = (HttpURLConnection) url.openConnection();
                String lastModified = ucon.getHeaderField("Last-Modified");
                ucon = (HttpURLConnection) url.openConnection();
                if(Target.exists())
                {
                    ucon.setRequestProperty("Range", "bytes=" + Target.length() + "-");
                    ucon.setRequestProperty("If-Range", lastModified);
                }
                ucon.setReadTimeout(TIMEOUT_CONNECTION);
                ucon.setConnectTimeout(TIMEOUT_SOCKET);
                ucon.connect();


                Callisto.notification_download.setLatestEventInfo(context.getApplicationContext(),
                        Callisto.RESOURCES.getString(R.string.downloading) + " " +
                                Callisto.current_download + " " +
                                Callisto.RESOURCES.getString(R.string.of) + " " +
                                Callisto.downloading_count + " (contd.)",
                        Show + ": " + Title, contentIntent);
                mNotificationManager.notify(NOTIFICATION_ID, Callisto.notification_download);

                InputStream is = ucon.getInputStream();
                TotalSize = ucon.getContentLength() + Target.length();
                BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);
                FileOutputStream outStream;
                byte buff[];
                Log.i("EpisodeDesc:DownloadTask", "mmk skipping the downloaded..." + Target.length() + " of " + TotalSize);
                if(Target.exists())
                {
                    //inStream.skip(Target.length());

                    outStream = new FileOutputStream(Target, true);
                }
                else
                    outStream = new FileOutputStream(Target);
                buff = new byte[5 * 1024];
                Log.i("EpisodeDesc:DownloadTask", "Getting content length (size)");
                int len = 0;
                long downloadedSize = Target.length(),
                        perc = 0;

                int SPD_COUNT = 200,
                        dli = 0;
                long lastTime = (new java.util.Date()).getTime(),
                        all_spds = 0;
                double avg_speed = 0;
                DecimalFormat df = new DecimalFormat("#.##");

                Log.i("EpisodeDesc:DownloadTask", "FINALLY starting the download");
                WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if(DownloadList.Download_wifiLock==null)
                    DownloadList.Download_wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL , "Callisto_download");
                if(!DownloadList.Download_wifiLock.isHeld())
                    DownloadList.Download_wifiLock.acquire();

                inner_failures = 0;
                //Here is where the actual downloading happens
                while (len != -1)
                {
                    Log.i("EpisodeDesc:DownloadTask", "DERP: " + downloadedSize);
                    dlList = PreferenceManager.getDefaultSharedPreferences(context).getString("ActiveDownloads", "");
                    if(dlList.equals("") || !(Long.parseLong(dlList.substring(1,dlList.indexOf('|',1)))==id))
                    {
                        Log.i("EpisodeDesc:DownloadTask", "DERPDADSADSA");
                        Target.delete();
                        break;
                    }
                    if(isCancelled())
                    {
                        mNotificationManager.cancel(NOTIFICATION_ID);
                        return false;
                    }

                    try
                    {
                        len = inStream.read(buff);
                        if(len==-1)
                            break;


                        outStream.write(buff,0,len);
                        downloadedSize += len;
                        perc = downloadedSize*100;
                        perc /= TotalSize;

                        //Add to the average speed
                        long temp_spd = 0;
                        long time_diff = ((new java.util.Date()).getTime() - lastTime);
                        if(time_diff>0)
                        {
                            temp_spd= len*100/time_diff;
                            dli++;
                            all_spds += temp_spd;
                            lastTime = (new java.util.Date()).getTime();
                        }

                        } catch (IOException e) {
                            Log.e("EpisodeDesc:DownloadTask:IOException", "IO is a moon - " + inner_failures);
                            inner_failures++;
                            if(inner_failures==INNER_LIMIT)
                                break;
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e1) {}
                            //Add failure to average
                            dli++;
                            lastTime = (new java.util.Date()).getTime();

                        } catch (Exception e) {
                            Log.e("EpisodeDesc:DownloadTask:??Exception", e.getClass() + " : " + e.getMessage());
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e1) {}
                            //Add failure to average
                            dli++;
                            lastTime = (new java.util.Date()).getTime();
                        }

                        //If the time is right, do it!
                        if(dli>=SPD_COUNT)
                        {
                            avg_speed = all_spds*1.0/dli/100;
                            all_spds = 0;
                            dli = 0;

                            if(DownloadList.downloadProgress!=null)
                            {
                                int x = (int)(downloadedSize*100/TotalSize);
                                DownloadList.downloadProgress.setMax((int)(TotalSize/1000));
                                DownloadList.downloadProgress.setProgress((int)(downloadedSize/1000));
                            }
                            Callisto.notification_download.setLatestEventInfo(context.getApplicationContext(),
                                    Callisto.RESOURCES.getString(R.string.downloading) + " " +
                                            Callisto.current_download + " " +
                                            Callisto.RESOURCES.getString(R.string.of) + " " +
                                            Callisto.downloading_count + ": " + perc + "%  (" +
                                            df.format(avg_speed) + "kb/s)",
                                    Show + ": " + Title, contentIntent);
                            mNotificationManager.notify(NOTIFICATION_ID, Callisto.notification_download);
                        }
                }



                outStream.flush();
                outStream.close();
                inStream.close();
                dlList = PreferenceManager.getDefaultSharedPreferences(context).getString("ActiveDownloads", "");
                if(inner_failures==INNER_LIMIT)
                {

                    throw new Exception("Inner exception has passed " + INNER_LIMIT);
                }
                if(!dlList.equals("") && (Long.parseLong(dlList.substring(1,dlList.indexOf('|',1)))==id))
                {
                    Log.i("EpisodeDesc:DownloadTask", "Trying to finish with " + Target.length() + "==" + TotalSize);
                    if(Target.length()==TotalSize)
                    {
                        Callisto.current_download++;

                        Log.i("EpisodeDesc:DownloadTask", (inner_failures<INNER_LIMIT?"Successfully":"FAILED") + " downloaded to : " + Target.getPath());

                         //Move the download from active to completed.
                        DownloadList.removeDownload(context, DownloadList.ACTIVE, id, isVideo);
                        DownloadList.addDownload(context, DownloadList.COMPLETED, id, isVideo);

                        Log.i("EpisodeDesc:DownloadTask", " " + DownloadList.notifyUpdate);
                        if(DownloadList.notifyUpdate!=null)
                            DownloadList.notifyUpdate.sendEmptyMessage(0);

                        boolean queue = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("download_to_queue", false);
                        if(queue)
                            Callisto.databaseConnector.appendToQueue(id, false, isVideo);
                    }
                } else
                    Target.delete();
            }catch (ParseException e) {
                Log.e("EpisodeDesc:DownloadTask:ParseException", "Error parsing a date from the SQLite db: ");
                Log.e("EpisodeDesc:DownloadTask:ParseException", Date);
                Log.e("EpisodeDesc:DownloadTask:ParseException", "(This should never happen).");
                outer_failures++;
                e.printStackTrace();
            } catch(Exception e) {
                outer_failures++;
                Log.e("EEEEEEEE " + e.getClass(), "[" + outer_failures + "] Msg: " + e.getMessage());
                e.printStackTrace();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {}
            }
            if(outer_failures==OUTER_LIMIT)
            {
                //Add to end of active, but with x signifier to show it failed
                SharedPreferences.Editor e = PreferenceManager.getDefaultSharedPreferences(context).edit();
                String aDownloads = PreferenceManager.getDefaultSharedPreferences(context).getString("ActiveDownloads", "");
                aDownloads = aDownloads.replace("|" + Long.toString(id) + "|", "|");
                aDownloads = aDownloads.concat("x" + Long.toString(id) + "|");
                if(aDownloads.equals("|"))
                    aDownloads="";
                boolean quit = false;
                if(quit = aDownloads.charAt(1)=='x')
                    aDownloads = aDownloads.replaceAll("x","");
                e.putString("ActiveDownloads", aDownloads);
                e.commit();
                Log.i("EpisodeDesc:DownloadTask", "New aDownloads: " + aDownloads);
                if(DownloadList.notifyUpdate!=null)
                    DownloadList.notifyUpdate.sendEmptyMessage(0);
                failed++;
                outer_failures=0;

                if(quit)
                    break;
            }
        }


        if(DownloadList.Download_wifiLock!=null && DownloadList.Download_wifiLock.isHeld())
            DownloadList.Download_wifiLock.release();
        Log.i("EpisodeDesc:DownloadTask", "Finished Downloading");
        mNotificationManager.cancel(NOTIFICATION_ID);
        if(Callisto.downloading_count>0)
        {
            Callisto.notification_download = new Notification(R.drawable.ic_action_download, "Finished downloading " + Callisto.downloading_count + " files", NOTIFICATION_ID);
            Callisto.notification_download.setLatestEventInfo(context.getApplicationContext(), "Finished downloading " + Callisto.downloading_count + " files", failed>0 ? (failed + " failed, try them again later") : null, contentIntent);
            Callisto.notification_download.flags = Notification.FLAG_AUTO_CANCEL;
            mNotificationManager.notify(NOTIFICATION_ID, Callisto.notification_download);
            Callisto.current_download=1;
            Callisto.downloading_count=0;
        }
        else
        {
            Callisto.current_download=1;
            Callisto.downloading_count=0;
            return false;
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result)
    {
        running = false;
        Button streamButton = ((Button)((android.app.Activity)context).findViewById(R.id.stream)),
               downloadButton = ((Button)((android.app.Activity)context).findViewById(R.id.download));
        if(streamButton==null || downloadButton==null)
            return;
        if(result)
        {
            streamButton.setText(Callisto.RESOURCES.getString(R.string.play));
            streamButton.setOnClickListener(((EpisodeDesc)context).launchPlay);
            downloadButton.setText(Callisto.RESOURCES.getString(R.string.delete));
            downloadButton.setOnClickListener(((EpisodeDesc)context).launchDelete);
        } else
        {
            streamButton.setText(Callisto.RESOURCES.getString(R.string.stream));
            streamButton.setOnClickListener(((EpisodeDesc)context).launchStream);
            downloadButton.setText(Callisto.RESOURCES.getString(R.string.download));
            downloadButton.setOnClickListener(((EpisodeDesc)context).launchDownload);
        }
    }
}