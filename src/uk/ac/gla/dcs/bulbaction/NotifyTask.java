package uk.ac.gla.dcs.bulbaction;

import java.io.IOException;

import org.apache.http.client.methods.HttpPost;

import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;

class NotifyTask extends AsyncTask<HttpPost, Void, String> {
	public static final String TAG = "BulbActionNotifyTask";

	@Override
	protected String doInBackground(HttpPost... reqs) {
		AndroidHttpClient ahc = AndroidHttpClient.newInstance(TAG);
		try {
			return ahc.execute(reqs[0]).getStatusLine().getReasonPhrase();
		} catch (IOException e) {
			return "ERROR notifying the BulbRouter: " + e.getMessage();
		} finally {
			ahc.close();
		}
	}

	/**
	 * Uses the logging framework to display the output of the fetch operation
	 * in the log fragment.
	 */
	@Override
	protected void onPostExecute(String result) {
		Log.d(TAG, result);
	}

}