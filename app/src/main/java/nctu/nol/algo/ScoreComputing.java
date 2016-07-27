package nctu.nol.algo;


import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import nctu.nol.bt.devices.SoundWaveHandler;
import nctu.nol.file.LogFileWriter;
import nctu.nol.file.SystemParameters;

public class ScoreComputing {
    private final static String TAG = ScoreComputing.class.getSimpleName();

    /* Audio Data Related */
    private SoundWaveHandler curSW; //用來獲得SoundWaveHandler的物件, 目的是為了取得取樣時的音訊資料
    private LinkedBlockingQueue<WindowScore> AllWindowScores_for_file = new LinkedBlockingQueue<WindowScore>(); //用來儲存所有的Window時間、分數資訊(寫檔用)
    private LinkedBlockingQueue<WindowScore> AllWindowScores_for_algo = new LinkedBlockingQueue<WindowScore>(); //用來儲存所有的Window時間、分數資訊(分析用)

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

    /* 變數初始化 */
    private void initParameters(){
        AllWindowScores_for_algo.clear();
        AllWindowScores_for_file.clear();
    }

    /* 啟動Thread持續計算Window分數 */
    public void StartComputingScore(final List<HashMap.Entry<Float, Float>> FreqBands, final int SamplingRate, final int w_size){
        /*
        *   用Thread去觀察curSW內音訊資料數量, 每512點就必須計算一個WindowScore
        *   計算出來的結果請存到AllWindowScores內
        * */
        initParameters();
        computing_t = new Thread() {
            public void run() {
                // Initial Parameter
                List<Integer> FreqIdxs = new ArrayList<Integer>();
                float FreqMax = Float.NEGATIVE_INFINITY;

                // Wait Flag = true
                while (!SystemParameters.isServiceRunning.get());

                for (int i = 0; i < FreqBands.size(); i++) {
                    float freq = FreqBands.get(i).getKey();
                    float power = FreqBands.get(i).getValue();
                    FreqIdxs.add((int) (freq * w_size / SamplingRate));
                    if (FreqMax < power)
                        FreqMax = power;
                }

                // Start Counting Window
                while (SystemParameters.isServiceRunning.get()) {
                    final LinkedBlockingQueue<SoundWaveHandler.AudioData> curSample = curSW.getSampleData();

                    // Count window score, check w_size points a time.
                    if (curSample.size() > w_size) {
                        float w_dataset[] = new float[w_size];
                        long w_timestamp = 0;

                        // Copy data in specific window, and find timestamp of max value
                        float max = Float.NEGATIVE_INFINITY;
                        for (int i = 0; i < w_size; i++) {
                            SoundWaveHandler.AudioData ad = curSample.poll();
                            float w_data = ad.data;
                            w_dataset[i] = w_data;
                            if (max < Math.abs(w_data)) {
                                w_timestamp = ad.time;
                                max = Math.abs(w_data);
                            }
                        }

                        // Count score with specific freq bands and dataset
                        float score = CountWindowScore(w_dataset, FreqIdxs, FreqMax);
                        WindowScore w_score = new WindowScore(w_timestamp, score);
                        AllWindowScores_for_file.add(w_score);
                        AllWindowScores_for_algo.add(w_score);

                        // Log.d(TAG, w_timestamp + " " + score);
                    }
                }
            }
        };
        computing_t.start();
    }

    public static float CountWindowScore(final float[] dataset, final List<Integer> FreqIdxs, final float FreqMax){
        CountSpectrum CS = new CountSpectrum();
        float score = 0;
        for (int j = 0; j < FreqIdxs.size(); j++) {
            float power = CS.dft_specific_idx(FreqIdxs.get(j), dataset);
            score += power / FreqMax;
        }
        return score;
    }

    /* 啟動Thread寫檔, 紀錄每個Window的分數 */
    public void StartLogging(){
        ScoreWriter = new LogFileWriter("WindowScore.csv", LogFileWriter.WINDOW_SCORE_TYPE, LogFileWriter.TESTING_TYPE);
        logging_t = new Thread(){
            public void run(){
                isWrittingWindowScore.set(true);
                while( SystemParameters.isServiceRunning.get() || AllWindowScores_for_file.size() > 0){
                    if( AllWindowScores_for_file.size() > 0 ){
                        final WindowScore w_score = AllWindowScores_for_file.poll();
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
    public final LinkedBlockingQueue<WindowScore> getAllWindowScores(){
        return AllWindowScores_for_algo;
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
