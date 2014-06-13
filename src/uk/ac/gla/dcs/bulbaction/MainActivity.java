package uk.ac.gla.dcs.bulbaction;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Calendar;

import org.apache.http.client.methods.HttpPost;
import org.json.JSONArray;
import org.json.JSONException;

import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;
import android.provider.Settings;
import android.app.Activity;
import android.app.Notification;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

	

	//	Calendar crap begins
	public static final String[] INSTANCE_PROJECTION = new String[] {
	    Instances.EVENT_ID,
	    Instances.BEGIN,
	    Instances.END,
	    Instances.AVAILABILITY,
	    Instances.ALL_DAY
	  };
	  
	//Calendar crap ends
	
	
	
	Intent service;
	AndroidHttpClient ahc;
	SharedPreferences prefs = null;
	static long calendarID = -1;
	static long lastRefreshed = -1;
	static boolean ignoreAllDay = false;
	
	//static Drawable redCircle, greenCircle, orangeCircle, grayCircle;
	
	static Drawable[] circles = new Drawable[4];
	//static int circlecnt = circles.length;
	
	static Button[] leds = new Button[8];
	int[] ledstates = {0, 0, 0, 0, 0, 0, 0, 0};
	long[] durations = {0, 0, 0, 0, 0, 0, 0, 0};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		service = new Intent(getApplicationContext(),
				NotificationListener.class);
		String notificationPermissions = Settings.Secure.getString(
				getContentResolver(), "enabled_notification_listeners");
		if (notificationPermissions != null
				&& notificationPermissions.contains(getPackageName())) {
			Log.v(Constants.LOG_TAG, "Verified notification permissions");
			//getApplicationContext().startService(service); /* the system should do it for us! */
		} else {
			// No permissions - run settings
			Log.v(Constants.LOG_TAG, "No permissions to listen for notifications");
			startActivity(new Intent(
					"android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
		}
		ahc = AndroidHttpClient.newInstance("BulbAction notifier");
		
		//prefs = getSharedPreferences("BulbAction", MODE_PRIVATE);
		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		calendarID = prefs.getLong("calendarID", -1);
		lastRefreshed = prefs.getLong("lastRefreshed", -1);
		ignoreAllDay = prefs.getBoolean("ignore_all_day", false);
		
		if (calendarID == -1)
			Toast.makeText(getApplicationContext(), "Please select your calendar in settings", Toast.LENGTH_SHORT).show();
		
		//set it to midnight today for comparison
		Calendar refreshCheck = Calendar.getInstance();
		refreshCheck.set(Calendar.HOUR_OF_DAY, 0);
		refreshCheck.set(Calendar.MINUTE, 0);
		refreshCheck.set(Calendar.SECOND, 0);
		
		if (lastRefreshed < refreshCheck.getTimeInMillis())
			refreshCalendar();		
		else {
			try {
				JSONArray ledstatespref = new JSONArray(prefs.getString("ledStates", ""));
				for (int i = 0; i < ledstates.length; ++i)
					ledstates[i] = ledstatespref.getInt(i);
				changeLeds();
			} catch (JSONException e) {
				//Awsnap, the array couldn't have been loaded. it's a pity. let's get the calendar then instead?
				refreshCalendar();
			}
		}
		
		//LED test part
		circles[0] = getResources().getDrawable(R.drawable.circle_gray);
		circles[1] = getResources().getDrawable(R.drawable.circle_green);
		circles[2] = getResources().getDrawable(R.drawable.circle_orange);
		circles[3] = getResources().getDrawable(R.drawable.circle_red);
		
		
		
		leds[0] = (Button) findViewById(R.id.LED01);
		leds[1] = (Button) findViewById(R.id.LED02);
		leds[2] = (Button) findViewById(R.id.LED03);
		leds[3] = (Button) findViewById(R.id.LED04);
		leds[4] = (Button) findViewById(R.id.LED05);
		leds[5] = (Button) findViewById(R.id.LED06);
		leds[6] = (Button) findViewById(R.id.LED07);
		leds[7] = (Button) findViewById(R.id.LED08);
		
		Button off = (Button) findViewById(R.id.offButton);
		Button busy = (Button) findViewById(R.id.busyButton);
		Button refresh = (Button) findViewById(R.id.refreshButton);
		
		off.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				for (int i = 0; i < ledstates.length; ++i)
					ledstates[i] = 0;
				updateDisplay();
			}
		});
		
		busy.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) {
				for (int i = 0; i < ledstates.length; ++i)
					ledstates[i] = 3;
				updateDisplay();
			}
		});
		
		refresh.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				refreshCalendar();
				updateDisplay();
			}
		});

		onLEDClickListener ocl = new onLEDClickListener();
				
		for (Button led: leds)
				led.setOnClickListener(ocl);
		
		updateDisplay();
	}
	
	public void refreshCalendar() {
		//Get Calendar events here
		

				// Specify the date range you want to search for recurring
				// event instances
				Calendar beginTime = Calendar.getInstance();
				beginTime.set(Calendar.HOUR_OF_DAY, 9);
				beginTime.set(Calendar.MINUTE, 00);

				long startMillis = beginTime.getTimeInMillis();
				
				
				Calendar endTime = Calendar.getInstance();
				endTime.set(Calendar.HOUR_OF_DAY, 16);
				endTime.set(Calendar.MINUTE, 59);
				long endMillis = endTime.getTimeInMillis();
				
				//Log.d(TAG, "startMillis: "+startMillis);

				Cursor cur = null;
				ContentResolver cr = getContentResolver();

				/*
				// The ID of the recurring event whose instances you are searching
				// for in the Instances table
				String selection = Instances.EVENT_ID + " = ?";
				String[] selectionArgs = new String[] {"207"};
				*/
				
				// Construct the query with the desired date range.
				Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
				//ContentUris.appendId(builder, calendarID);
				ContentUris.appendId(builder, startMillis);
				ContentUris.appendId(builder, endMillis);

				// Submit the query
				/*cur =  cr.query(builder.build(), 
				    INSTANCE_PROJECTION, 
				    selection, 
				    selectionArgs, 
				    null);*/
				
				cur = cr.query(builder.build(), INSTANCE_PROJECTION, null, null, null);
				   
				Calendar human = Calendar.getInstance();
				
				//reset durations array
				for (int i = 0; i < durations.length; ++i)
					durations[i] = 0;
				
				while (cur.moveToNext()) {
				    long eventID = 0;
				    long beginVal = 0;    
				    long endVal  = 0;
				    int avail = -1;
				    int allday = 0;
				    // Get the field values
				    eventID = cur.getLong(0);
				    beginVal = cur.getLong(1);
				    endVal = cur.getLong(2);
				    avail = cur.getInt(3);
				    allday = cur.getInt(4);
				    
				    //Discount the availability set to "free"
				    if (avail == Instances.AVAILABILITY_FREE)
				    	continue;
				    
				    //Ignore all day events if the user chose to do so
				    if (ignoreAllDay && allday == 1)
				    	continue;
				    
				    human.setTimeInMillis(beginVal);
				    //Log.d(TAG, "Event "+eventID+" starting at "+beginVal+" ("+human.get(Calendar.HOUR_OF_DAY)+")");
				    
				    //int hourIdx = (int)(beginVal-startMillis)/3600000; rounding errors!!!
				    
				    int hourIdx = human.get(Calendar.HOUR_OF_DAY)-9;
				    int duration = (int)(endVal-beginVal)/60000; //minutes!
				    
				    for (int i = hourIdx; duration > 0; ++i) { //update durations array to indicate availability
				    	if (i < durations.length && i >= 0) {
				    	durations[i] += duration;}
				    	duration -= 60;
				    }
			
				    for (int i = 0; i < durations.length; ++i) {
				    	Log.d(Constants.LOG_TAG, "Duration at "+i+": "+durations[i]);
				    }
				}
				
				cur.close();
				
				//1 hour = 60 minutes = 3600 seconds = 3600000 ms
				for (int i = 0; i < 8; ++i) { // integers representing 8 working hours from 9 to 5
					if ( durations[i] > 30 )
						ledstates[i] = 3;
					else if (durations[i] > 15)
						ledstates[i] = 2;
					else
						ledstates[i] = 1;
				}
				
				lastRefreshed = Calendar.getInstance().getTimeInMillis();
				prefs.edit().putLong("lastRefreshed", lastRefreshed).commit();
	}
	
	class onLEDClickListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			int index = Arrays.asList(leds).indexOf(v);
			//Log.d(TAG, "LED "+index+" has been touched - "+ledstates[index]);
			ledstates[index] = (ledstates[index]+1) % circles.length;
			v.setBackground(circles[ledstates[index]]);
			changeLeds();
		}
		
	}

	public void updateDisplay() {
		for (int i = 0; i < ledstates.length; ++i)
			leds[i].setBackground(circles[ledstates[i]]);
		changeLeds();
	}
	
	@Override
	protected void onDestroy() {
		JSONArray ledstatesprefs;
		try {
			ledstatesprefs = new JSONArray(ledstates);
			prefs.edit().putString("ledStates", ledstatesprefs.toString()).commit();
		} catch (JSONException e) {
			//Well, tough
			Log.e(Constants.LOG_TAG, "Could not save LED states!");
		}
		ahc.close();
		stopService(service);
		super.onDestroy();
	}
	
	@Override
	protected void onPause() {
		JSONArray ledstatesprefs;
		try {
			ledstatesprefs = new JSONArray(ledstates);
			prefs.edit().putString("ledStates", ledstatesprefs.toString()).commit();
		} catch (JSONException e) {
			//Well, tough
			Log.e(Constants.LOG_TAG, "Could not save LED states!");
		}
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		ignoreAllDay = prefs.getBoolean("ignore_all_day", false);
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
			Parcelable[] rawMsgs = getIntent().getParcelableArrayExtra(
					NfcAdapter.EXTRA_NDEF_MESSAGES);
			if (rawMsgs != null) {
				NdefMessage msgs[] = new NdefMessage[rawMsgs.length];
				for (int i = 0; i < rawMsgs.length; i++) {
					msgs[i] = (NdefMessage) rawMsgs[i];
				}
				NdefRecord frecord = msgs[0].getRecords()[0];
				byte[] payload = frecord.getPayload();
				String txtm = new String(Arrays.copyOfRange(payload, 3,
						payload.length));
				handleNfcText(txtm);
			}
		}
	}

	private void changeLeds() {
		//Log.d(TAG, "Entered changeLeds()");
		Uri.Builder ub = Uri.parse(Constants.URL).buildUpon();

		ub.appendPath("availastrip");
		String states = "";
		for (int state:ledstates)
			states += state;
		ub.appendQueryParameter("pattern", states);
		final HttpPost req = new HttpPost(URI.create(ub.build().toString()));

		NotifyTask a = new NotifyTask();
		a.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, req); 
	}
	
	private boolean handleNfcText(String message) {
		Toast.makeText(getApplicationContext(),
				"Registering tag " + message, Toast.LENGTH_SHORT)
				.show();
		Uri.Builder ub = Uri.parse(Constants.URL).buildUpon();

		ub.appendPath("nfctag");
		ub.appendQueryParameter("txt", message);
		final HttpPost req = new HttpPost(URI.create(ub.build().toString()));

		AsyncTask<Void, Void, Void> t = new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				try {
					ahc.execute(req);
				} catch (IOException e) {

					e.printStackTrace();
				}
				return null;
			}
		};
		t.execute();
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		case R.id.action_perms:
			startActivity(new Intent(
					"android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
			return true;
		case R.id.action_notifications:
			startActivity(new Intent(this, EditNotificationActivity.class));
			return true;

		}
		return super.onOptionsItemSelected(item);
	}

}
