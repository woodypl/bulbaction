package uk.ac.gla.dcs.bulbaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.json.JSONArray;
import org.json.JSONException;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleAckReceiver;
import com.getpebble.android.kit.PebbleKit.PebbleNackReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.SpannableString;
import android.text.format.Time;
import android.util.Log;

public class NotificationListener extends NotificationListenerService {

	AndroidHttpClient ahc;
	SharedPreferences sp;
	static String gaccount = "";
	private Constants.Mode prefMode;
	private String[] pkgList;

	public NotificationListener() {
		Log.v(Constants.LOG_TAG, "NotificationListener instantiated.");
		ahc = AndroidHttpClient.newInstance("BulbAction notifier");
	}

	@Override
	public void onCreate() {
		Log.v(Constants.LOG_TAG, "NotificationListener created.");
		// Get the settings
		sp = getApplicationContext().getSharedPreferences(Constants.LOG_TAG,
				MODE_MULTI_PROCESS | MODE_PRIVATE);
		prefMode = Constants.Mode.values()[sp.getInt(Constants.PREFERENCE_MODE,
				Constants.Mode.OFF.ordinal())];
		String pkgs = sp.getString(Constants.PREFERENCE_PACKAGE_LIST, "");
		pkgList = pkgs.split(",");
		
		AccountManager manager = (AccountManager) this.getBaseContext()
				.getSystemService(ACCOUNT_SERVICE);
		Account[] list = manager.getAccounts();
		for (Account a : list) {
			if (a.type.equals("com.google")) {
				gaccount = a.name;
			}
		}
		getBaseContext().registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				/* Decode the message, if any
				 * String data = intent.getStringExtra("notificationData");
				 * JSONArray arr; try { arr = new JSONArray(data); String body =
				 * arr.getJSONObject(0).getString("body"); Log.d(TAG,
				 * "Received a Pebble notification: "+body);
				 * 
				 * } catch (JSONException e) { // TODO Auto-generated catch
				 * block e.printStackTrace(); }
				 */
				// If we're here, a 3rd party notification is being pushed to
				// Pebble
				pebbleFired();
			}

		}, new IntentFilter("com.getpebble.action.SEND_NOTIFICATION"));
		PebbleKit.registerReceivedAckHandler(getApplicationContext(),
				new PebbleAckReceiver(Constants.PEBBLE_APP_UUID) {
					@Override
					public void receiveAck(Context context, int transactionId) {
						Log.i(Constants.LOG_TAG,
								"Received ack for transaction " + transactionId);
					}
				});

		PebbleKit.registerReceivedNackHandler(getApplicationContext(),
				new PebbleNackReceiver(Constants.PEBBLE_APP_UUID) {
					@Override
					public void receiveNack(Context context, int transactionId) {
						Log.i(Constants.LOG_TAG,
								"Received nack for transaction "
										+ transactionId);
					}
				});

		PebbleKit.registerReceivedDataHandler(this,
				new PebbleKit.PebbleDataReceiver(Constants.PEBBLE_APP_UUID) {
					@Override
					public void receiveData(final Context context,
							final int transactionId, final PebbleDictionary data) {
						Log.i(Constants.LOG_TAG,
								"Received value=" + data.getInteger(1)
										+ " for key: 1");

						PebbleKit.sendAckToPebble(getApplicationContext(),
								transactionId);
						Log.d(Constants.LOG_TAG, "Cancelling all notifications");
						/*
						 * NotificationManager manager = (NotificationManager)
						 * getApplicationContext()
						 * .getSystemService(NOTIFICATION_SERVICE);
						 * 
						 * manager.cancelAll();
						 */
						NotificationListener.this.cancelAllNotifications();
					}
				});
	}

	public void pebbleFired() {
		Log.d(Constants.LOG_TAG, "Pebble notification fired");
		Context c = getApplicationContext();
		if (PebbleKit.isWatchConnected(c)) {
			Log.d(Constants.LOG_TAG,
					"Pebble connected, telling the app to listen for taps");
			PebbleKit.startAppOnPebble(c, Constants.PEBBLE_APP_UUID);
			PebbleDictionary data = new PebbleDictionary();
			data.addUint8(0, (byte) 1);
			PebbleKit.sendDataToPebble(c, Constants.PEBBLE_APP_UUID, data);
		}

	}

	public int onStopCommand(Intent intent, int flags, int startid) {
		return 0;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startid) {

		try {
			for (StatusBarNotification sbn : getActiveNotifications()) {
				dispatchSBN(sbn);
				Log.v(Constants.LOG_TAG, "From " + sbn.getPackageName());
				printNotification(sbn.getNotification());
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(Constants.LOG_TAG, "Can't get active notifications!");
			stopSelf();
		}
		return START_STICKY;
	}

	@Override
	public void onNotificationPosted(StatusBarNotification sbn) {
		dispatchSBN(sbn, false);
		// Notification not = sbn.getNotification();
		// Log.v(TAG, "From " + sbn.getPackageName() + sbn.getTag());
		// printNotification(not);

	}

	@Override
	public void onNotificationRemoved(StatusBarNotification sbn) {
		// Log.v(TAG, "A notification was removed:");
		// printNotification(sbn.getNotification());
		dispatchSBN(sbn, true);
	}

	@Override
	public void onDestroy() {
		ahc.close();
		Log.v(Constants.LOG_TAG, "NotificationListener removed.");
		super.onDestroy();
	}

	void printNotification(Notification not) {
		String desc = "Notification ";
		desc += "for " + not.number + " events ";
		desc += "with colour " + not.ledARGB;
		desc += " - " + not.tickerText;

	}

	void dispatchSBN(StatusBarNotification sbn) {
		dispatchSBN(sbn, false);
	}

	void dispatchSBN(StatusBarNotification sbn, boolean remove) {
		String pkg = sbn.getPackageName();
		Notification not = sbn.getNotification();
		int msgcount = not.number;

		Bitmap senderIcon;

		{
			switch (prefMode) {
			case EXCLUDE:
				if (Arrays.asList(pkgList).contains(pkg))
					// Do not forward!
					return;
			case INCLUDE:
				if (!(Arrays.asList(pkgList).contains(pkg)))
					return;
			case OFF:
			default: // Do nothing
			}
		}

		if (Arrays.asList(Constants.PEBBLE_ALLOWED_PACKAGES).contains(pkg)
				&& !remove) {
			pebbleFired();
		}

		if (not.largeIcon != null) {
			senderIcon = not.largeIcon;
		} else { // must be set
			Context remote;
			try {
				remote = createPackageContext(pkg, 0);
				senderIcon = BitmapFactory.decodeResource(
						remote.getResources(), not.icon);
			} catch (NameNotFoundException e) {
				senderIcon = null;
				Log.e(Constants.LOG_TAG,
						"Could not find the icon for the notification!");
			}

		}

		Uri.Builder ub = Uri.parse(Constants.URL).buildUpon();

		if (remove) {
			ub.appendPath("denotify");
		} else {
			ub.appendPath("notify");
		}

		ub.appendQueryParameter("id", Integer.toString(sbn.getId()));

		ub.appendQueryParameter("count", Integer.toString(msgcount));
		ub.appendQueryParameter("pkg", pkg);

		if (not.tickerText != null)
			ub.appendQueryParameter("title", not.tickerText.toString());
		else {
			String title = not.extras.getString(Notification.EXTRA_TITLE);
			ub.appendQueryParameter("title", title);
		}

		Object txt = not.extras.get(Notification.EXTRA_TEXT);
		if (txt != null)
			ub.appendQueryParameter("txt", txt.toString());
		if (gaccount != "")
			ub.appendQueryParameter("account", gaccount);

		HttpPost req = new HttpPost(URI.create(ub.build().toString()));

		if (senderIcon != null) {
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			senderIcon.compress(CompressFormat.PNG, 100, bo);
			ByteArrayEntity entity = new ByteArrayEntity(bo.toByteArray());

			req.setEntity(entity);
		}

		NotifyTask a = new NotifyTask();
		a.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, req);

	}

}
