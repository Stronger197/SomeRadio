package com.github.guwenk.smuradio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {


    private static long back_pressed;
    final String FBDB_RATE_VAL = "rate";
    final String FBDB_RATE_COUNT = "count";
    protected PlayerService playerService;
    protected ServiceConnection serviceConnection;
    protected float rateValue;
    protected long rateCount;
    private TitleString titleString;
    private DatabaseReference mRootRef = FirebaseDatabase.getInstance().getReference();
    private DatabaseReference mRatingRef = mRootRef.child("Rating");
    private SharedPreferences sPref;
    private ImageView backgroundImage;
    private TextView title;
    private TextView ratingTV;

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unbindService(serviceConnection);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sPref = PreferenceManager.getDefaultSharedPreferences(this);
        backgroundImage = (ImageView) findViewById(R.id.main_backgroundImage);
        title = (TextView) findViewById(R.id.main_status1);
        titleString = new TitleString();
        ratingTV = (TextView) findViewById(R.id.main_ratingTV);
        final Button btnToTrackOrder = (Button) findViewById(R.id.main_btnToTrackOrder);
        btnToTrackOrder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (new InternetChecker().hasConnection(getApplicationContext())) {
                    long order_freeze = sPref.getLong(Constants.OTHER.ORDER_FREEZE, 0);
                    long current_time = System.currentTimeMillis();
                    if (current_time >= order_freeze || order_freeze == 0) {
                        Intent intent = new Intent(MainActivity.this, OrderActivity.class);
                        startActivity(intent);
                    } else {
                        int seconds_left = (int) ((order_freeze - current_time) / 1000);
                        Toast.makeText(getApplicationContext(), getString(R.string.order_freeze_msg_one) + seconds_left + getString(R.string.order_freeze_msg_two), Toast.LENGTH_SHORT).show();
                    }

                }
            }
        });
        title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                ClipboardManager clipboard = (ClipboardManager) getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("", title.getText());
                clipboard.setPrimaryClip(clipData);
                Toast.makeText(getApplicationContext(), R.string.name_copied, Toast.LENGTH_SHORT).show();
            }
        });

        Intent intent = new Intent(MainActivity.this, PlayerService.class);
        startService(intent);
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                playerService = ((PlayerService.MyBinder) iBinder).getService();
                //playerService.registerClient(MainActivity.this);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                playerService = null;
            }
        };
        bindService(intent, serviceConnection, 0);

        final ImageButton btnPlay = (ImageButton) findViewById(R.id.main_play_button);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ImageButton imgBtn = (ImageButton) findViewById(R.id.main_play_button);
                findViewById(R.id.main_status1).setVisibility(View.VISIBLE);
                imgBtn.setImageResource(R.drawable.ic_stop);
                Intent intent = new Intent(MainActivity.this, PlayerService.class);
                intent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
                startService(intent);
            }
        });
        sPref.registerOnSharedPreferenceChangeListener(this);

        RatingBar ratingBar = (RatingBar) findViewById(R.id.main_RatingBar);
        ratingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(final RatingBar ratingBar, float rating, boolean fromUser) {
                if (fromUser) {
                    char[] chars = titleString.getTitle().toCharArray();
                    for (int i = 0; i < chars.length; i++){
                        if (chars[i] == '.' || chars[i] == '#' || chars[i] == '$' || chars[i] == '[' || chars[i] == ']'){
                            chars[i] = ' ';
                        }
                    }
                    mRatingRef.child(String.valueOf(chars)).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            rateCount = 0;
                            rateValue = 0;
                            try {
                                rateCount = dataSnapshot.child(FBDB_RATE_COUNT).getValue(Long.class);
                                rateValue = dataSnapshot.child(FBDB_RATE_VAL).getValue(Float.class);
                            } catch (NullPointerException ignored) {
                            }
                            String s = String.format("%.2f", rateValue / rateCount);
                            ratingTV.setText(!s.equals("NaN") ? s : getString(R.string.zero));
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                    mRatingRef.child(titleString.getTitle()).child(FBDB_RATE_VAL).setValue(rateValue + rating);
                    mRatingRef.child(titleString.getTitle()).child(FBDB_RATE_COUNT).setValue(rateCount + 1);
                    ratingBar.setIsIndicator(true);
                    sPref.edit().putFloat(Constants.OTHER.USER_RATE + titleString.getTitle(), rating).apply();
                }
            }
        });
    }


    @Override
    protected void onStart() {
        super.onStart();
        onSharedPreferenceChanged(sPref, Constants.MESSAGE.PLAYER_STATUS);
        String path = sPref.getString(Constants.PREFERENCES.BACKGROUND_PATH, "");
        Bitmap backgroundBitmap;
        if (path.equals("")) {
            backgroundImage.setImageResource(R.drawable.main_background);
        } else {
            backgroundBitmap = new FileManager(getApplicationContext()).loadBitmap(path, Constants.PREFERENCES.BACKGROUND);
            backgroundImage.setImageBitmap(backgroundBitmap);
        }

        Intent intent = new Intent(MainActivity.this, PlayerService.class);
        bindService(intent, serviceConnection, 0);
    }


    @Override
    public void onBackPressed() {
        if (back_pressed + 2000 > System.currentTimeMillis())
            super.onBackPressed();
        else
            Toast.makeText(getBaseContext(), R.string.on_back_pressed,
                    Toast.LENGTH_SHORT).show();
        back_pressed = System.currentTimeMillis();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_toAdminActivity:
                intent = new Intent(MainActivity.this, AdminActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_copy:
                ClipboardManager clipboard = (ClipboardManager) getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("", sPref.getString(Constants.PREFERENCES.LINK, getString(R.string.link_128)));
                clipboard.setPrimaryClip(clipData);
                Toast.makeText(getApplicationContext(), getString(R.string.link_was_copied), Toast.LENGTH_SHORT).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case Constants.MESSAGE.MUSIC_TITLE:
            case Constants.MESSAGE.PLAYER_STATUS: {
                ImageButton imageButton = (ImageButton) findViewById(R.id.main_play_button);
                int player_status = sharedPreferences.getInt(Constants.MESSAGE.PLAYER_STATUS, -1);
                String player_title = sharedPreferences.getString(Constants.MESSAGE.MUSIC_TITLE, "");
                switch (player_status) {
                    case 0: {
                        title.setText("");
                        titleString.setTitle("");
                        findViewById(R.id.main_status1).setVisibility(View.INVISIBLE);
                        imageButton.setImageResource(R.drawable.ic_play_arrow);
                        break;
                    }
                    case 1: {
                        title.setText(player_title);
                        if (!titleString.getTitle().equals(player_title))
                            titleString.setTitle(player_title);
                        findViewById(R.id.main_status1).setVisibility(View.VISIBLE);
                        imageButton.setImageResource(R.drawable.ic_stop);
                        break;
                    }
                }
                break;
            }
        }
    }

    private class TitleString {
        private String title;
        private RatingBar ratingBar = (RatingBar) findViewById(R.id.main_RatingBar);

        TitleString() {
            title = "";
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;

            ratingBar.setRating((float) 0);
            if (!titleString.getTitle().equals("") && !titleString.getTitle().equals(getString(R.string.connecting)) && !titleString.getTitle().equals(getString(R.string.default_status))) {
                char[] chars = titleString.getTitle().toCharArray();
                for (int i = 0; i < chars.length; i++){
                    if (chars[i] == '.' || chars[i] == '#' || chars[i] == '$' || chars[i] == '[' || chars[i] == ']'){
                        chars[i] = ' ';
                    }
                }
                mRatingRef.child(String.valueOf(chars)).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        rateCount = 0;
                        rateValue = 0;
                        try {
                            rateCount = dataSnapshot.child(FBDB_RATE_COUNT).getValue(Long.class);
                            rateValue = dataSnapshot.child(FBDB_RATE_VAL).getValue(Float.class);
                        } catch (NullPointerException ignored) {
                        }
                        String s = String.format("%.2f", rateValue / rateCount);
                        ratingTV.setText(!s.equals("NaN") ? s : getString(R.string.zero));
                        ratingBar.setVisibility(View.VISIBLE);
                        ratingTV.setVisibility(View.VISIBLE);
                        float user_rate_from_pref = sPref.getFloat(Constants.OTHER.USER_RATE + getTitle(), 0);
                        if (user_rate_from_pref != 0) {
                            ratingBar.setRating(user_rate_from_pref);
                            ratingBar.setIsIndicator(true);
                        } else ratingBar.setIsIndicator(false);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            } else {
                ratingBar.setIsIndicator(true);
                ratingTV.setText("");
                ratingTV.setVisibility(View.INVISIBLE);
                ratingBar.setVisibility(View.INVISIBLE);
            }
        }
    }
}
