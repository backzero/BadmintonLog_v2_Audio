package nctu.nol.algo;


import android.support.v4.util.LogWriter;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import nctu.nol.bt.devices.SoundWaveHandler;
import nctu.nol.file.LogFileWriter;
import nctu.nol.file.SystemParameters;

public class ScoreComputing {
    private final static String TAG = ScoreComputing.class.getSimpleName();

    /* Audio Data Related */
    private SoundWaveHandler curSW; //用來獲得SoundWaveHandler的物件, 目的是為了取得取樣時的音訊資料
    private Vector<WindowScore> AllWindowScores = new Vector<WindowScore>(); //用來儲存所有的Window時間、分數資訊

    /* Thread Related */
    private Thread computing_t;
    private Thread logging_t;

    /* Logging Related */
    private LogFileWriter ScoreWriter;
    public AtomicBoolean isWrittingWindowScore = new AtomicBoolean(false);

    /* Constructor, Class內的初始化(參數之類的), 在new這個Class的時候會自動觸發 */
    public ScoreComputing(SoundWaveHandler sw){
        this.curSW = sw;
    }

    /* 啟動Thread持續計算Window分數 */
    public void StartComputingScore(final List<HashMap.Entry<Float, Float>> FreqBands, final int SamplingRate, final int w_size){
        /*
        *   用Thread去觀察curSW內音訊資料數量, 每512點就必須計算一個WindowScore
        *   計算出來的結果請存到AllWindowScores內
        *   ps. 必須要有變數去儲存目前算到哪裡
        *
        * */
        computing_t = new Thread() {
            public void run() {
                // Initial Parameter
                int ptr_i = 0;
                List<Integer> FreqIdxs = new ArrayList<Integer>();
                float FreqMax = Float.MIN_VALUE;

                for(int i = 0; i < FreqBands.size(); i++) {
                    float freq = FreqBands.get(i).getKey();
                    float power =  FreqBands.get(i).getValue();
                    FreqIdxs.add( (int)(freq*w_size/SamplingRate) );
                    if( FreqMax < power )
                        FreqMax = power;
                }

                // Start Counting Window
                while(SystemParameters.isServiceRunning.get()){
                    final Vector<SoundWaveHandler.AudioData> curSample =  curSW.getSampleData();
                    final int length = curSample.size(); // prevent size change when thread is in for loop

                    // Count window score, check w_size points a time.
                    for(int i = ptr_i; i+w_size < length; i+=w_size){

                        float w_dataset[] = new float[w_size];
                        long w_timestamp = 0;

                        // Copy data in specific window, and find timestamp of max value
                        float max = Float.MIN_VALUE;
                        for(int j = i; j < i+w_size; j++) {
                            float w_data = curSample.get(j).data;
                            w_dataset[j-ptr_i] = w_data;
                            if( max < w_data )
                                w_timestamp = curSample.get(j).time;
                        }

                        // Count score with specific freq bands and dataset
                        CountSpectrum CS = new CountSpectrum();
                        float score = 0;
                        for(int j = 0; j < FreqIdxs.size(); j++) {
                            float power = CS.dft_specific_idx(FreqIdxs.get(j),w_dataset);
                            score += power/FreqMax;
                        }
                        WindowScore w_score = new WindowScore(w_timestamp, score);
                        AllWindowScores.add(w_score);

                        Log.d(TAG, w_timestamp+" "+score);

                        ptr_i += w_size;
                    }
                }
            }
        };
        computing_t.start();
    }

    /* 啟動Thread寫檔, 紀錄每個Window的分數 */
    public void StartLogging(){
        ScoreWriter = new LogFileWriter("WindowScore.csv", LogFileWriter.WINDOW_SCORE_TYPE, LogFileWriter.TESTING_TYPE);
        logging_t = new Thread(){
            public void run(){
                int CurrentWriteWindowIndex = 0;
                isWrittingWindowScore.set(true);
                while( SystemParameters.isServiceRunning.get() || CurrentWriteWindowIndex < AllWindowScores.size()){
                    int curSize = AllWindowScores.size();
                    for(; CurrentWriteWindowIndex < curSize ;CurrentWriteWindowIndex++){
                        final WindowScore w_score = AllWindowScores.get(CurrentWriteWindowIndex);
                        try {
                            ScoreWriter.writeWindowScore(w_score.w_time, w_score.score);
                        } catch (IOException e) {
                            Log.e(TAG,e.getMessage());
                        }
                    }

                }
                if(ScoreWriter != null)
                    ScoreWriter.closefile();
                isWrittingWindowScore.set(false);
            }
        };
        logging_t.start();
    }

    /* 其他Class要使用AllWindowScores時, 會用到的函式 */
    public final Vector<WindowScore> getAllWindowScores(){
        return AllWindowScores;
    }

    /* 用來儲存每個Window的時間以及分數 */
    public class WindowScore{
        public long w_time; //用來儲存Window內最大值的時間戳記
        public float score; //用來儲存Window經計算後的分數

        /* Constructor, Class內的初始化(參數之類的), 在new這個Class的時候會自動觸發 */
        public WindowScore(long timestamp, float score){
            this.w_time = timestamp;
            this.score = score;
        }
    }


}
