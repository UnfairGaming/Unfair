package cn.unfair.util;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import paulscode.sound.ICodec;
import paulscode.sound.SoundBuffer;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class CodecMp3 implements ICodec {

    public static void register() {
        try {
            SoundSystemConfig.setCodec("mp3", CodecMp3.class);
        } catch (SoundSystemException ignored) {
        }
    }

    private Bitstream bitstream;
    private Decoder decoder;
    private InputStream inputStream;
    private AudioFormat audioFormat;
    private boolean initialized;
    private boolean endOfStream;

    @Override
    public void reverseByteOrder(boolean b) {
    }

    @Override
    public boolean initialize(URL url) {
        this.cleanup();
        try {
            this.inputStream = new BufferedInputStream(url.openStream());
            this.bitstream = new Bitstream(this.inputStream);
            this.decoder = new Decoder();
            this.initialized = true;
            this.endOfStream = false;
            return true;
        } catch (Exception e) {
            this.cleanup();
            return false;
        }
    }

    @Override
    public boolean initialized() {
        return this.initialized;
    }

    @Override
    public SoundBuffer read() {
        if (!this.initialized || this.endOfStream) {
            return null;
        }

        try {
            Header header = this.bitstream.readFrame();
            if (header == null) {
                this.endOfStream = true;
                return null;
            }

            SampleBuffer sampleBuffer = (SampleBuffer) this.decoder.decodeFrame(header, this.bitstream);
            this.bitstream.closeFrame();
            this.ensureFormat();

            short[] samples = sampleBuffer.getBuffer();
            int length = sampleBuffer.getBufferLength();
            byte[] data = new byte[length * 2];
            for (int i = 0; i < length; i++) {
                data[i * 2] = (byte) (samples[i] & 0xFF);
                data[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
            }
            return new SoundBuffer(data, this.audioFormat);
        } catch (Exception e) {
            this.endOfStream = true;
            return null;
        }
    }

    @Override
    public SoundBuffer readAll() {
        if (!this.initialized || this.endOfStream) {
            return null;
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Header header;

            while ((header = this.bitstream.readFrame()) != null) {
                SampleBuffer sampleBuffer = (SampleBuffer) this.decoder.decodeFrame(header, this.bitstream);
                this.bitstream.closeFrame();
                this.ensureFormat();

                short[] samples = sampleBuffer.getBuffer();
                int length = sampleBuffer.getBufferLength();
                for (int i = 0; i < length; i++) {
                    output.write(samples[i] & 0xFF);
                    output.write((samples[i] >> 8) & 0xFF);
                }
            }

            this.endOfStream = true;
            byte[] data = output.toByteArray();
            return data.length == 0 ? null : new SoundBuffer(data, this.audioFormat);
        } catch (Exception e) {
            this.endOfStream = true;
            return null;
        }
    }

    private void ensureFormat() {
        if (this.audioFormat == null && this.decoder != null) {
            this.audioFormat = new AudioFormat(
                    this.decoder.getOutputFrequency(),
                    16,
                    this.decoder.getOutputChannels(),
                    true,
                    false
            );
        }
    }

    @Override
    public boolean endOfStream() {
        return this.endOfStream;
    }

    @Override
    public void cleanup() {
        if (this.bitstream != null) {
            try {
                this.bitstream.close();
            } catch (Exception ignored) {
            }
            this.bitstream = null;
        }

        if (this.inputStream != null) {
            try {
                this.inputStream.close();
            } catch (IOException ignored) {
            }
            this.inputStream = null;
        }

        this.decoder = null;
        this.audioFormat = null;
        this.initialized = false;
        this.endOfStream = false;
    }

    @Override
    public AudioFormat getAudioFormat() {
        return this.audioFormat;
    }
}
