package view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.vikhyat.reflex.MainActivity;
import com.vikhyat.reflex.R;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;

import static android.widget.Toast.*;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ReflexView extends View {

    //static instance variables
    private static final String HIGH_SCORE ="HIGH_SCORE";
    private SharedPreferences preferences;

    //variables that manage the game
    private int spotsTouched;
    private int score;
    private int level;
    private int viewWidth;
    private int viewHeight;
    private long animationTime;
    private boolean gameOver;
    private boolean gamePaused;
    private boolean dialogDisplayed;
    private int highScore;

    //adding collections for our spots(imageviews) and animations
    private final Queue<ImageView> spots = new ConcurrentLinkedDeque<>();
    private final Queue<Animator> animators= new ConcurrentLinkedDeque<>();

    private TextView highScoreTextView;
    private TextView ScoreTextView;
    private TextView levelTextView;
    private LinearLayout livesLinearLayout;
    private Resources resources;
    private RelativeLayout relativeLayout;
    private LayoutInflater layoutInflater;

    public static final int INITIAL_ANIMATION_DURATION = 6000; //6 sec
    public static final Random random = new Random();
    public static final int SPOT_DIAMETER = 100;
    public static final float SCALE_X = 0.25f;
    public static final float SCALE_Y = 0.25f;
    public static final int INITIAL_LIVES = 3;
    public static final int SPOT_DELAY = 500;
    public static final int LIVES = 3;
    public static final int MAX_LIVES =7;
    public static final int NEW_LEVEL = 10;
    private Handler spotHandler;
    public static final int HIT_SOUND_ID =1;
    public static final int MISS_SOUND_ID= 2;
    public static final int DISAPPEAR_SOUND_ID = 3;
    public static final int SOUND_PRIORITY = 1;
    public static final int SOUND_QUALITY=100;
    public static final int INITIAL_SPOTS= 5;
    public static final int MAX_STREAMS = 4;
    private SoundPool.Builder soundPool;
    private float volume;
    private float maxVolume;
    private boolean loaded;
    private Map<Integer,Integer> soundMap;

    public ReflexView(Context context, SharedPreferences sharedPreferences, RelativeLayout parentLayout) {
        //this shared preference is used to store some data in it like the levels is stored so that when the user comes back he can see it.
        super(context);
        preferences = sharedPreferences;
        highScore = preferences.getInt(HIGH_SCORE,0);

        //save the resources for loading values
        resources= context.getResources();

        //save layoutInflater
        layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        //setup UI components
        relativeLayout = parentLayout;
        livesLinearLayout = parentLayout.findViewById(R.id.LifeLinearLayout);
        highScoreTextView = parentLayout.findViewById(R.id.highScoreTextView);
        levelTextView= parentLayout.findViewById(R.id.levelTextView);
        ScoreTextView = parentLayout.findViewById(R.id.scoreTextView);

        spotHandler = new Handler();
//        addNewSpot();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        viewWidth= w;
        viewHeight =h;
    }//end method onSizeChanged

    public void pause(){
        gamePaused = true;
        soundPool.build().release();
        soundPool= null;
        cancelAnimations();
    }
    private void  cancelAnimations() {
        for (Animator animator:animators){
            animator.cancel();
        }
        //remove remaining spots from the screen
        for (ImageView view:spots){
            relativeLayout.removeView(view);
        }
        spotHandler.removeCallbacks(addSpotRunnable);
        animators.clear();
        spots.clear();
    }//end of cancelanimations

    public void resume(Context context){
        gamePaused= false;
        initializeSoundEffects(context);//initialize app's SoundPool
        if (!dialogDisplayed)
            resetGame();//start the game
    }//end of resume
    public void resetGame(){

        spots.clear();
        animators.clear();
        livesLinearLayout.removeAllViews();

        animationTime = INITIAL_ANIMATION_DURATION;//init animation length
        spotsTouched=0;
        score = 0;
        level =1;
        gameOver = false;
        displayScores();

        //add lives
        for (int i=0;i<LIVES;i++){
            livesLinearLayout.addView(
                    (ImageView)layoutInflater.inflate(R.layout.life,null));
        }

        for (int i=1 ;i<INITIAL_SPOTS;i++){
            spotHandler.postDelayed(addSpotRunnable, i* SPOT_DELAY);
        }
    }//end of reset game
    //creating app's sound pool for playing game audio
    private void initializeSoundEffects(Context context){
        AudioAttributes aa = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_GAME)
                .build();

        soundPool = new SoundPool.Builder();

        soundPool.setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(aa)
                .build();
        soundPool.build().setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener(){
            @Override
            public void onLoadComplete(SoundPool soundPool,int sampleId,int status){
                loaded = true;
                Log.d("Loaded ",String.valueOf(loaded));
            }
        });
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
        maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        volume = volume/maxVolume;


        //creating a sound map
        soundMap = new HashMap<Integer,Integer>();
        soundMap.put(HIT_SOUND_ID,soundPool.build().load(context,R.raw.hit,SOUND_PRIORITY));
        soundMap.put(MISS_SOUND_ID,soundPool.build().load(context,R.raw.miss,SOUND_PRIORITY));
        soundMap.put(DISAPPEAR_SOUND_ID,soundPool.build().load(context,R.raw.disappear,SOUND_PRIORITY));


    }

    private void displayScores(){
        highScoreTextView.setText(resources.getString(R.string.high_score)+ " "+ highScore);
        ScoreTextView.setText(resources.getString(R.string.score) + " "+ score);
        levelTextView.setText(resources.getString(R.string.level) + " " + level);
    }
    private Runnable addSpotRunnable = new Runnable(){
        @Override
        public void run() {
            addNewSpot();
        }
    };//end of runnable



    public void addNewSpot() {


        //create the spot
        final ImageView spot = (ImageView) layoutInflater.inflate(R.layout.untouched, null);
        spots.add(spot);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) spot.getContext().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;


        //if we are leaving it for random no it is taking as less than SPOT_DIAMETER and which is causing negative no
        int x = random.nextInt(width - SPOT_DIAMETER);
        int y = random.nextInt(height - SPOT_DIAMETER);
        int x2 = random.nextInt(width - SPOT_DIAMETER);
        int y2 = random.nextInt(height - SPOT_DIAMETER);

//        now set the parameters which we used to set by using xml
        spot.setLayoutParams(new RelativeLayout.LayoutParams(SPOT_DIAMETER, SPOT_DIAMETER));

        spot.setImageResource(random.nextInt(2) == 0 ? R.drawable.green_spot : R.drawable.red_spot);
//        if the value is equal to 0 place a green spot or a red spot
        spot.setX(x);
        spot.setY(y);

        spot.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
//                Log.d("SPOT CLICK","clicked!");
                touchedSpot(spot);

            }
        });


        relativeLayout.addView(spot);
//    adding the animations for the spots

        spot.animate().x(x2).y(y2).scaleX(SCALE_X).scaleY(SCALE_Y)
                .setDuration(animationTime).setListener(new AnimatorListenerAdapter() {
            /**
             * {@inheritDoc}
             *
             * @param animation
             */
            @Override
            public void onAnimationCancel(Animator animation) {
                animators.remove(animation);//remove the animations
            }

            /**
             * {@inheritDoc}
             *
             * @param animation
             */
            @Override
            public void onAnimationStart(Animator animation) {
                animators.add(animation);//save for later time
            }

            /**
             * {@inheritDoc}
             *
             * @param animation
             */
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                if (!gamePaused && spots.contains(spot)) {//not touched
                    missedSpot(spot);//lose a life
                }
            }//end of animation end
        });
    }//end of addnew spot
    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (soundPool != null)
            soundPool.build().play(HIT_SOUND_ID,volume,volume,SOUND_PRIORITY,0,1.0F);
            score -= 15*level;
            score = Math.max(score,0);//do not let the score become negative
            displayScores();
            return true;
        }
        private void touchedSpot(ImageView spot){
        relativeLayout.removeView(spot);//remove the spot that is touched
            spots.remove(spot);

            ++spotsTouched;
            score += 10 * level;
            if (soundPool != null)
                soundPool.build().play(HIT_SOUND_ID,volume,volume,SOUND_PRIORITY,0,1.0F);

            //increment level if player touched 10 spots in the current level
            if (spotsTouched % NEW_LEVEL ==0){
                ++level;
                animationTime *= 0.95;//make the game 5% faster than the older one
                if (livesLinearLayout.getChildCount() < MAX_LIVES){
                    ImageView life = (ImageView) layoutInflater.inflate(R.layout.life,null);
                }//end of if
            }//end of onTouchMotion listener

            displayScores();

            if (!gameOver)
                addNewSpot();//add another untouched spot
            }//end of

            private void missedSpot(ImageView spot) {
                spots.remove(spot);
                relativeLayout.removeView(spot);

                if (gameOver)
                    return;
                if (soundPool != null) {
                    soundPool.build().play(DISAPPEAR_SOUND_ID, volume, volume, SOUND_PRIORITY, 0, 1f);
                }
                if (livesLinearLayout.getChildCount() == 0) {
                    gameOver = true;

                    if (score > highScore) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt(HIGH_SCORE, score);
                        editor.apply();

                        highScore = score;
                    }

                    cancelAnimations();
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Game Over");
                    builder.setMessage("Score: " + score);
                    builder.setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            displayScores();
                            dialogDisplayed = false;
                            resetGame();
                        }
                    });
                    dialogDisplayed = true;

                    builder.show();
                    builder.
                }else{
                    livesLinearLayout.removeViewAt(
                            livesLinearLayout.getChildCount()-1);
                    addNewSpot();
            }//end of method missed spot
    }
}
