package uk.ac.gla.dcs.bulbaction;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.provider.CalendarContract.Calendars;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

public class CalendarListPreference extends ListPreference {

	private static long calendarID = -1;
	public static final String[] EVENT_PROJECTION = new String[] {
			Calendars._ID, // 0
			Calendars.ACCOUNT_NAME, // 1
			Calendars.CALENDAR_DISPLAY_NAME, // 2
			Calendars.OWNER_ACCOUNT // 3
	};

	private static final int PROJECTION_ID_INDEX = 0;
	private static final int PROJECTION_ACCOUNT_NAME_INDEX = 1;
	private static final int PROJECTION_DISPLAY_NAME_INDEX = 2;
	private static final int PROJECTION_OWNER_ACCOUNT_INDEX = 3;
	
	private CalendarArrayAdapter adapter = null;

	public CalendarListPreference(Context context) {
		super(context);
	}

	public CalendarListPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public static void saveCalendar(SharedPreferences p, long id) {
		SharedPreferences.Editor e = p.edit();
		e.putLong("calendarID", id);
		e.commit();
	}

	@Override
	protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
		Cursor cur = null;
		ContentResolver cr = getContext().getContentResolver();
		Uri uri = Calendars.CONTENT_URI;

		String selection = null;
		String[] selectionArgs = null;
		// Submit the query and get a Cursor object back.
		cur = cr.query(uri, EVENT_PROJECTION, selection, selectionArgs, null);

		ArrayList<CalendarPair> calendarList = new ArrayList<CalendarPair>();

		while (cur.moveToNext()) {
			long calID = 0;
			String displayName = null;
			String accountName = null;
			String ownerName = null;

			// Get the field values
			calID = cur.getLong(PROJECTION_ID_INDEX);
			displayName = cur.getString(PROJECTION_DISPLAY_NAME_INDEX);
			accountName = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX);
			ownerName = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX);
			calendarList.add(new CalendarPair(calID, displayName));
		}
		cur.close();
		
		adapter = new CalendarArrayAdapter(getContext(), calendarList);
		
		builder.setSingleChoiceItems(
				adapter,
				0,
				new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick( DialogInterface dialog, int which )
					{
						CalendarListPreference.saveCalendar(
							getSharedPreferences(),
							adapter.getItemId( which ) );

						CalendarListPreference.this.onClick(
							dialog,
							DialogInterface.BUTTON_POSITIVE );

						dialog.dismiss();
					}
				} );

			builder.setPositiveButton( null, null );

	}

	private class CalendarPair {
		public long id;
		public String name;

		public CalendarPair(long id, String name) {
			this.id = id;
			this.name = name;
		}
	}

	private static class CalendarViewHolder {
		private RadioButton radioButton;
		private TextView textView;

		public CalendarViewHolder() {
		}

		public CalendarViewHolder(TextView textView, RadioButton radioButton) {
			this.radioButton = radioButton;
			this.textView = textView;
		}

		public RadioButton getRadioButton() {
			return radioButton;
		}

		public void setCheckBox(RadioButton radioButton) {
			this.radioButton = radioButton;
		}

		public TextView getTextView() {
			return textView;
		}

		public void setTextView(TextView textView) {
			this.textView = textView;
		}
	}

	private static class CalendarArrayAdapter extends
			ArrayAdapter<CalendarPair> {

		private LayoutInflater inflater;
		private ArrayList<RadioButton> buttonList = new ArrayList<RadioButton>();

		public CalendarArrayAdapter(Context context,
				List<CalendarPair> calendarList) {
			super(context, R.layout.row_select_calendar, R.id.rowTextView,
					calendarList);
			if (calendarID == -1) {
				calendarID = PreferenceManager.getDefaultSharedPreferences(getContext()).getLong("calendarID", -1);
			}
			// Cache the LayoutInflate to avoid asking for a new one each time.
			inflater = LayoutInflater.from(context);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			CalendarPair calendar = (CalendarPair) this.getItem(position);

			// The child views in each row.
			RadioButton radioButton;
			TextView textView;

			// Create a new row view
			if (convertView == null) {
				convertView = inflater.inflate(R.layout.row_select_calendar,
						null);

				// Find the child views.
				textView = (TextView) convertView
						.findViewById(R.id.rowTextView);
				radioButton = (RadioButton) convertView
						.findViewById(R.id.radioButton01);

				buttonList.add(radioButton);

				// Optimization: Tag the row with it's child views, so we don't
				// have to
				// call findViewById() later when we reuse the row.
				convertView
						.setTag(new CalendarViewHolder(textView, radioButton));

				radioButton.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						RadioButton cb = (RadioButton) v;
						CalendarPair calendar = (CalendarPair) cb.getTag();
						calendarID = calendar.id;
						CalendarListPreference.saveCalendar(
								PreferenceManager.getDefaultSharedPreferences(getContext()),
								calendarID );
						for (RadioButton b : buttonList)
							b.setChecked(false);
						cb.setChecked(true);
					}
				});
			}
			// Reuse existing row view
			else {
				// Because we use a ViewHolder, we avoid having to call
				// findViewById().
				CalendarViewHolder viewHolder = (CalendarViewHolder) convertView
						.getTag();
				radioButton = viewHolder.getRadioButton();
				textView = viewHolder.getTextView();
			}

			radioButton.setTag(calendar);
			radioButton.setChecked(false);
			if (calendarID == calendar.id) {
				radioButton.setChecked(true);
			}
			textView.setText(calendar.name);

			return convertView;
		}

	}
}
