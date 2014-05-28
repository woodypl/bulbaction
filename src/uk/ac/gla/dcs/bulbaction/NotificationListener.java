package uk.ac.gla.dcs.bulbaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
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
import android.util.Log;

public class NotificationListener extends NotificationListenerService {

	final String TAG = "BulbAction";
	// final String URL = "http://10.10.10.28:8000/";
	//final String URL = "http://192.168.112.33:8000/";
	AndroidHttpClient ahc;
	static String gaccount = "";

	public NotificationListener() {
		Log.v(TAG, "NotificationListener instantiated.");
		ahc = AndroidHttpClient.newInstance("BulbAction notifier");
	}

	@Override
	public void onCreate() {
		Log.v(TAG, "NotificationListener created.");
		AccountManager manager = (AccountManager) this.getBaseContext().getSystemService(ACCOUNT_SERVICE);
		Account[] list = manager.getAccounts();
		for (Account a : list) {
			if (a.type.equals("com.google")) {
				gaccount = a.name;
			}
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
				Log.v(TAG, "From " + sbn.getPackageName());
				printNotification(sbn.getNotification());
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Can't get active notifications!");
			stopSelf();
		}
		return START_STICKY;
	}

	@Override
	public void onNotificationPosted(StatusBarNotification sbn) {
		dispatchSBN(sbn, false);
		//Notification not = sbn.getNotification();
		// Log.v(TAG, "From " + sbn.getPackageName() + sbn.getTag());
		// printNotification(not);

	}

	@Override
	public void onNotificationRemoved(StatusBarNotification sbn) {
		Log.v(TAG, "A notification was removed:");
		// printNotification(sbn.getNotification());
		dispatchSBN(sbn, true);
	}

	@Override
	public void onDestroy() {
		ahc.close();
		Log.v(TAG, "NotificationListener removed.");
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
		
		if (not.largeIcon != null) {
			senderIcon = not.largeIcon;
		}
		else { //must be set
			Context remote;
			try {
				remote = createPackageContext(pkg, 0);
				senderIcon  = BitmapFactory.decodeResource(remote.getResources(), not.icon);
			} catch (NameNotFoundException e) {
				senderIcon = null;
				Log.e(TAG, "Could not find the icon for the notification!");
			}
			
		}
		

		Uri.Builder ub = Uri.parse(MainActivity.URL).buildUpon();

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
