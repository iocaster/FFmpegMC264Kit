package kim.yt.ffmpegmc264;

public class LibFFmpegMC264 {
    static {
        System.loadLibrary("ffmpeg_jni");
        System.loadLibrary("avutil-56");
        System.loadLibrary("swresample-3");
        System.loadLibrary("avcodec-58");
        System.loadLibrary("avformat-58");
        System.loadLibrary("swscale-5");
        System.loadLibrary("avfilter-7");
        //System.loadLibrary("avdevice");
        //System.loadLibrary("ffmpeg_jni"); //moved up
    }

    private native void FFmpegReady();
    private native int ffmpegRun(String[] args);
    private native int ffmpegStop();
    private native int ffmpegForceStop();

    private static final String TAG = "LibFFmpegMC264";

    private MC264Encoder mMC264Encoder;
    private MCAACEncoder mMCAACEncoder;

    private int mHour, mMin, mSec, mMSec;                //for media duration
    private int mCurHour, mCurMin, mCurSec, mCurMSec;   //for current position
    private int mWidth, mHeight, mRotate;
    private float mBps;


    public LibFFmpegMC264() {
        mMC264Encoder = new MC264Encoder();
        mMCAACEncoder = new MCAACEncoder();

        //FFmpegReady();    //NOTE : User should call this function in the thread out of the UI thread
    }

    public static class Duration {
        public int hour, min, sec, msec;

        Duration( int hour, int min, int sec, int msec ) {
            this.hour = hour;
            this.min = min;
            this.sec = sec;
            this.msec = msec;
        }
    }
    public Duration getDuration() {
        return new Duration(mHour, mMin, mSec, mMSec);
    }

    public Duration getCurrentDuration() {
        return new Duration(mCurHour, mCurMin, mCurSec, mCurMSec);
    }

    public static class VideoInfo {
        public int width, height, rotate;
        public float bps;

        VideoInfo( int width, int height, int rotate, float bps ) {
            this.width = width;
            this.height = height;
            this.rotate = rotate;
            this.bps = bps;
        }
    }
    public VideoInfo getVideoInfo() {
        return new VideoInfo(mWidth, mHeight, mRotate, mBps);
    }


    public void Ready() {
        mHour = 0;         mMin = 0;         mSec = 0;         mMSec = 0;
        FFmpegReady();
    }

    public int Run(String[] args) {
        return ffmpegRun(args);
    }

    public int Stop() {
        return ffmpegStop();
    }

    public int ForceStop() {
        return ffmpegForceStop();
    }

    public void Reset() {
        mMC264Encoder.reset();
        mMCAACEncoder.reset();
    }

    public void enableScreenCapture( boolean value ) {
        mMC264Encoder.enableScreenGrabber(true);
    }

    public void enableMICCapture( boolean value ) {
        mMCAACEncoder.enableScreenGrabber(value);
    }

    public MC264Encoder getMC264Encoder() {
        return mMC264Encoder;
    }

    public MCAACEncoder getMCAACEncoder() {
        return mMCAACEncoder;
    }

    /*
     * onSetDuration(...) is called by jni
     */
    public void onSetDuration(int hour, int min, int sec, int msec) {
        mHour = hour;
        mMin = min;
        mSec = sec;
        mMSec = msec;
    }

    /*
     * onSetCurrentPosition(...) is called by jni
     */
    public void onSetCurrentPosition(int hour, int min, int sec, int msec) {
        mCurHour = hour;
        mCurMin = min;
        mCurSec = sec;
        mCurMSec = msec;
    }

    /*
     * onSetVideoInfo(...) is called by jni
     */
    public void onSetVideoInfo(int width, int height, int rotate, float bps) {
        if( width > 0 ) { mWidth = width; mRotate = 0; }
        if( height > 0 ) mHeight = height;
        if( rotate > 0 )  mRotate = rotate;
        if( bps > 0 ) mBps = bps;
    }
}
