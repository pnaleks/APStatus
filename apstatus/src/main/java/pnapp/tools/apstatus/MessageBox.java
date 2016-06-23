package pnapp.tools.apstatus;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
/**
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
public class MessageBox extends AppCompatActivity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_TEXT_1 = "text_1";
    public static final String EXTRA_TEXT_2 = "text_2";
    public static final String EXTRA_ACTION_1 = "action_1";
    public static final String EXTRA_ACTION_2 = "action_2";

    private PendingIntent intent1;
    private PendingIntent intent2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.message_box);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        if ( intent == null ) {
            finish();
            return;
        }

        if ( intent.hasExtra(EXTRA_TITLE) ) setTitle(intent.getCharSequenceExtra(EXTRA_TITLE));

        TextView text = (TextView) findViewById(R.id.text);
        if ( intent.hasExtra(EXTRA_TEXT) ) text.setText(intent.getCharSequenceExtra(EXTRA_TEXT));

        Button button1 = (Button) findViewById(R.id.button1);
        if ( intent.hasExtra(EXTRA_TEXT_1) ) button1.setText(intent.getCharSequenceExtra(EXTRA_TEXT_1));
        if ( intent.hasExtra(EXTRA_ACTION_1) ) intent1 = intent.getParcelableExtra(EXTRA_ACTION_1);


        Button button2 = (Button) findViewById(R.id.button2);
        if ( intent.hasExtra(EXTRA_TEXT_2) ) button2.setText(intent.getCharSequenceExtra(EXTRA_TEXT_2));
        if ( intent.hasExtra(EXTRA_ACTION_2) ) intent2 = intent.getParcelableExtra(EXTRA_ACTION_2);

        if ( intent1 == null ) {
            button1.setVisibility(View.GONE);
        } else if ( intent2 == null ) {
            button2.setVisibility(View.GONE);
        }

    }

    public void onButton1(View view) {
        if ( intent1 != null )
            try { intent1.send(); } catch (PendingIntent.CanceledException ignore) {}
        finish();
    }

    public void onButton2(View view) {
        if (intent2 != null )
            try { intent2.send(); } catch (PendingIntent.CanceledException ignore) {}
        finish();
    }

    @SuppressWarnings("unused")
    public static PendingIntent compose(Context context, int id, int titleResId, int textResId, NotificationCompat.Action action1, NotificationCompat.Action action2) {
        Intent intent = new Intent(context.getClass().getName(), Uri.parse("id:" + id), context, MessageBox.class);
        intent
                .putExtra(EXTRA_TITLE, context.getString(titleResId))
                .putExtra(EXTRA_TEXT, context.getString(textResId));

        if ( action1 != null ) {
            intent
                    .putExtra(MessageBox.EXTRA_TEXT_1, action1.getTitle())
                    .putExtra(MessageBox.EXTRA_ACTION_1, action1.getActionIntent());
        }

        if ( action2 != null ) {
            intent
                    .putExtra(MessageBox.EXTRA_TEXT_2, action2.getTitle())
                    .putExtra(MessageBox.EXTRA_ACTION_2, action2.getActionIntent());
        }

        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressWarnings("unused")
    public static PendingIntent compose(int id, NotificationCompat.Builder builder) {
        Intent intent = new Intent(builder.mContext.getClass().getName(), Uri.parse("id:" + id), builder.mContext, MessageBox.class);
        intent
                .putExtra(EXTRA_TITLE, builder.mContentTitle)
                .putExtra(EXTRA_TEXT, builder.mContentText);

        NotificationCompat.Action action;

        action = builder.mActions.get(0);
        if ( action != null ) {
            intent
                    .putExtra(MessageBox.EXTRA_TEXT_1, action.getTitle())
                    .putExtra(MessageBox.EXTRA_ACTION_1, action.getActionIntent());
        }

        action = builder.mActions.get(1);
        if ( action != null ) {
            intent
                    .putExtra(MessageBox.EXTRA_TEXT_2, action.getTitle())
                    .putExtra(MessageBox.EXTRA_ACTION_2, action.getActionIntent());
        }

        return PendingIntent.getActivity(builder.mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
