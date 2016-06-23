package pnapp.tools.apstatus;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONObject;

import java.util.ArrayList;

/**
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
public class PromotionActivity extends AppCompatActivity {
    public static final int REQ_CODE_PURCHASE = 777;

    public static final String BILLING_PACKAGE = "com.android.vending";
    public static final String BILLING_INTENT_ACTION = "com.android.vending.billing.InAppBillingService.BIND";

    public static final String DONATE_ITEM_ID = "gratuity";

    public static final String INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    //public static final String INAPP_DATA_SIGNATURE = "INAPP_DATA_SIGNATURE";

    public static final String ITEM_ID_LIST = "ITEM_ID_LIST";
    public static final String DETAILS_LIST = "DETAILS_LIST";
    public static final String BUY_INTENT = "BUY_INTENT";
    //public static final String INAPP_PURCHASE_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST";
    public static final String INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
    //public static final String INAPP_DATA_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
    //public static final String INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";

    public static final String RESPONSE_CODE = "RESPONSE_CODE";

    public static final int RESULT_SUCCESS = 0;
    //public static final int RESULT_CANCELED = 1;
    //public static final int RESULT_SERVICE_UNAVAILABLE = 2;
    //public static final int RESULT_BILLING_UNAVAILABLE = 3;
    //public static final int RESULT_ITEM_UNAVAILABLE = 4;
    //public static final int RESULT_DEVELOPER_ERROR = 5;
    //public static final int RESULT_API_ERROR = 6;
    //public static final int RESULT_ITEM_ALREADY_OWNED = 7;
    //public static final int RESULT_ITEM_NOT_OWNED = 8;
    public static final int RESULT_UNKNOWN = -1;

    IInAppBillingService mBillingService;

    ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBillingService = IInAppBillingService.Stub.asInterface(service);
            Base.log("PROMO > Billing Service connected");
            View view = findViewById(R.id.donate);
            if ( view != null ) view.setEnabled(false);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if ( getPurchaseDataList() ) consume();
                    if ( getSku() ) runOnUiThread(new Runnable() { @Override public void run() { enableDonate(); } });
                }
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Base.log("PROMO > Billing Service disconnected");
            mBillingService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Base.log("======== PromotionActivity Created ========");

        setContentView(R.layout.promotion);
        setOnClickListeners(findViewById(R.id.page));

        Intent intent = new Intent();
        intent.setPackage(BILLING_PACKAGE);
        intent.setAction(BILLING_INTENT_ACTION);

        bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if ( mBillingService != null ) unbindService(mServiceConnection);
        Base.log("-------- PromotionActivity Destroyed --------");
    }

    private void setOnClickListeners(View view) {
        if ( view == null ) return;
        if ( view instanceof ViewGroup ) {
            ViewGroup viewGroup = (ViewGroup) view;
            for(int i = 0; i < viewGroup.getChildCount(); i++) {
                setOnClickListeners(viewGroup.getChildAt(i));
            }
            return;
        }
        if ( view.getTag() != null )  view.setOnClickListener( onClickListener );
    }

    View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            String tag = (String) view.getTag();
            if ( tag == null ) return;
            Uri webPage = null;
            switch (tag) {
                case "pnapp.tools.apstatus":
                    webPage = Uri.parse( getString(R.string.tools_apstatus_url) );
                    break;
                case "pnapp.pncalc.free":
                    webPage = Uri.parse( getString(R.string.pncalc_free_url) );
                    break;
                case "pnapp.pncalc.full":
                    webPage = Uri.parse( getString(R.string.pncalc_full_url) );
                    break;
                case "pnapp.tools.ping":
                    webPage = Uri.parse( getString(R.string.tools_ping_url) );
                    break;
                case "blogger":
                    webPage = Uri.parse(getString(R.string.blogger_url));
                    webPage = webPage.buildUpon()
                            .appendQueryParameter("u", getString(R.string.tools_apstatus_url))
                            .appendQueryParameter("n", getString(R.string.share_title))
                            .appendQueryParameter("t", getString(R.string.share_text))
                            .build();
                    break;
                case "facebook":
                    webPage = Uri.parse(getString(R.string.facebook_url));
                    webPage = webPage.buildUpon()
                            .appendQueryParameter("u", getString(R.string.tools_apstatus_url))
                            .build();
                    break;
                case "google_plus":
                    webPage = Uri.parse( getString(R.string.google_plus_url) );
                    webPage = webPage.buildUpon()
                            .appendQueryParameter("url", getString(R.string.tools_apstatus_url))
                            .build();
                    break;
                case "live_journal":
                    webPage = Uri.parse( getString(R.string.live_journal_url) );
                    webPage = webPage.buildUpon()
                            .appendQueryParameter("subject", getString(R.string.share_title))
                            .appendQueryParameter("event", "<a href=\"" + getString(R.string.tools_apstatus_url) + "\">" + getString(R.string.share_title) + "</a> " + getString(R.string.share_text))
                            .build();
                    break;
            }

            if ( webPage != null ) {
                Base.log( webPage.toString() );
                Intent intent = new Intent(Intent.ACTION_VIEW, webPage);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }
    };


    public void onDonate(View view) { purchase(); }

    protected void purchase() {
        if ( mBillingService == null ) return;

        try {
            Bundle bundle = mBillingService.getBuyIntent(3, getPackageName(), DONATE_ITEM_ID, "inapp", null);
            int res = bundle.getInt(RESPONSE_CODE, RESULT_UNKNOWN);
            if ( res != RESULT_SUCCESS ) throw new RuntimeException("Response code = " + res);
            PendingIntent pendingIntent = bundle.getParcelable(BUY_INTENT);
            if ( pendingIntent == null ) throw  new RuntimeException("Buy Intent is null");
            startIntentSenderForResult(pendingIntent.getIntentSender(), REQ_CODE_PURCHASE, new Intent(),0,0,0);
            Base.log("PROMO > Buy Intent Sender Successfully started!");
        } catch ( Exception e ) {
            Base.log("PROMO > Exception at purchase: " + e.toString());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ( requestCode != REQ_CODE_PURCHASE ) return;

        if ( resultCode == RESULT_OK ) {
            Base.log("PROMO > Donation was successful");
            String purchaseData = data.getStringExtra(INAPP_PURCHASE_DATA);
            if ( purchaseData != null ) {
                mPurchaseDataList.add(purchaseData);
                new Thread(new Runnable() { @Override public void run() { consume(); } }).start();
            }
        } else {
            Base.log("PROMO > Donation canceled");
        }
    }

    String mSkuPrice;
    String mSkuTitle;
    String mSkuDescription;

    ArrayList<String> mPurchaseDataList = new ArrayList<>();

    @SuppressLint("SetTextI18n")
    private void enableDonate() {
        Button button = (Button) findViewById(R.id.donate);
        if ( button != null ) {
            button.setText(mSkuTitle + " " + mSkuPrice);
            button.setEnabled(true);
        }

        TextView textView = (TextView) findViewById(R.id.donate_description);
        if ( textView != null ) {
            textView.setText(mSkuDescription);
        }
    }

    private boolean getSku() {
        ArrayList<String> skuList = new ArrayList<>();
        skuList.add(DONATE_ITEM_ID);
        Bundle query = new Bundle();
        query.putStringArrayList(ITEM_ID_LIST, skuList);

        try {
            Bundle bundle = mBillingService.getSkuDetails(3, getPackageName(), "inapp", query);
            int res = bundle.getInt(RESPONSE_CODE, RESULT_UNKNOWN);
            if ( res != RESULT_SUCCESS ) throw new RuntimeException("Response code is " + res);
            ArrayList<String> list = bundle.getStringArrayList(DETAILS_LIST);
            if ( list == null || list.isEmpty() ) throw new RuntimeException("Response Data List is null or empty");
            JSONObject jData = new JSONObject(list.get(0));
            mSkuTitle = jData.getString("title");
            mSkuDescription = jData.getString("description");
            mSkuPrice = jData.getString("price");
            return true;
        } catch (Exception e) {
            Base.log("PROMO > Error at getSku: " + e.toString());
            return false;
        }
    }

    private boolean consume() {
        if ( mPurchaseDataList.isEmpty() ) {
            Base.log("PROMO > Nothing to consume!");
            return true;
        }
        for ( String data : mPurchaseDataList ) {
            try {
                JSONObject jData = new JSONObject(data);
                String productId = jData.getString("productId");
                String purchaseToken = jData.getString("purchaseToken");
                if ( mBillingService == null ) return false;
                int res = mBillingService.consumePurchase(3, getPackageName(), purchaseToken);
                if (res != RESULT_SUCCESS)
                    throw new RuntimeException("Consumption of " + productId + " failed with " + res);
                Base.log("PROMO > " + productId + " consumed successfully");
            } catch (Exception e) {
                Base.log("PROMO > Error at Consumer: " + e.toString());
                return false;
            }
        }

        mPurchaseDataList.clear();
        return true;
    }

    private boolean getPurchaseDataList() {
        try {
            if ( mBillingService == null ) throw new RuntimeException("Billing service disconnected");
            Bundle bundle = mBillingService.getPurchases(3, getPackageName(), "inapp", null);
            int res = bundle.getInt(RESPONSE_CODE, RESULT_UNKNOWN);
            if ( res != RESULT_SUCCESS ) throw new RuntimeException("GetPurchases results " + res);
            ArrayList<String> list = bundle.getStringArrayList(INAPP_PURCHASE_DATA_LIST);
            if ( list == null ) throw new RuntimeException("INAPP_PURCHASE_DATA_LIST is null");
            mPurchaseDataList.addAll(list);
            Base.log("PROMO > Added " + list.size() + " purchases to mPurchaseDataList");
            return true;
        } catch (Exception e) {
            Base.log("PROMO > Error at getPurchaseDataList: " + e.toString());
            return false;
        }
    }
 }
