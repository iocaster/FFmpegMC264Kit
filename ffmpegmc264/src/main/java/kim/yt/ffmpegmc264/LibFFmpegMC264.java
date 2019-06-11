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

    public LibFFmpegMC264() {
        mMC264Encoder = new MC264Encoder();
        mMCAACEncoder = new MCAACEncoder();

        //FFmpegReady();    //NOTE : User should call this function in the thread out of the UI thread
    }

    public void Ready() {
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
}
