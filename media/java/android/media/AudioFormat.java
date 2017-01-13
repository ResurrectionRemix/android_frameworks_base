/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Objects;

/**
 * The {@link AudioFormat} class is used to access a number of audio format and
 * channel configuration constants. They are for instance used
 * in {@link AudioTrack} and {@link AudioRecord}, as valid values in individual parameters of
 * constructors like {@link AudioTrack#AudioTrack(int, int, int, int, int, int)}, where the fourth
 * parameter is one of the <code>AudioFormat.ENCODING_*</code> constants.
 * The <code>AudioFormat</code> constants are also used in {@link MediaFormat} to specify
 * audio related values commonly used in media, such as for {@link MediaFormat#KEY_CHANNEL_MASK}.
 * <p>The {@link AudioFormat.Builder} class can be used to create instances of
 * the <code>AudioFormat</code> format class.
 * Refer to
 * {@link AudioFormat.Builder} for documentation on the mechanics of the configuration and building
 * of such instances. Here we describe the main concepts that the <code>AudioFormat</code> class
 * allow you to convey in each instance, they are:
 * <ol>
 * <li><a href="#sampleRate">sample rate</a>
 * <li><a href="#encoding">encoding</a>
 * <li><a href="#channelMask">channel masks</a>
 * </ol>
 * <p>Closely associated with the <code>AudioFormat</code> is the notion of an
 * <a href="#audioFrame">audio frame</a>, which is used throughout the documentation
 * to represent the minimum size complete unit of audio data.
 *
 * <h4 id="sampleRate">Sample rate</h4>
 * <p>Expressed in Hz, the sample rate in an <code>AudioFormat</code> instance expresses the number
 * of audio samples for each channel per second in the content you are playing or recording. It is
 * not the sample rate
 * at which content is rendered or produced. For instance a sound at a media sample rate of 8000Hz
 * can be played on a device operating at a sample rate of 48000Hz; the sample rate conversion is
 * automatically handled by the platform, it will not play at 6x speed.
 *
 * <p>As of API {@link android.os.Build.VERSION_CODES#M},
 * sample rates up to 192kHz are supported
 * for <code>AudioRecord</code> and <code>AudioTrack</code>, with sample rate conversion
 * performed as needed.
 * To improve efficiency and avoid lossy conversions, it is recommended to match the sample rate
 * for <code>AudioRecord</code> and <code>AudioTrack</code> to the endpoint device
 * sample rate, and limit the sample rate to no more than 48kHz unless there are special
 * device capabilities that warrant a higher rate.
 *
 * <h4 id="encoding">Encoding</h4>
 * <p>Audio encoding is used to describe the bit representation of audio data, which can be
 * either linear PCM or compressed audio, such as AC3 or DTS.
 * <p>For linear PCM, the audio encoding describes the sample size, 8 bits, 16 bits, or 32 bits,
 * and the sample representation, integer or float.
 * <ul>
 * <li> {@link #ENCODING_PCM_8BIT}: The audio sample is a 8 bit unsigned integer in the
 * range [0, 255], with a 128 offset for zero. This is typically stored as a Java byte in a
 * byte array or ByteBuffer. Since the Java byte is <em>signed</em>,
 * be careful with math operations and conversions as the most significant bit is inverted.
 * </li>
 * <li> {@link #ENCODING_PCM_16BIT}: The audio sample is a 16 bit signed integer
 * typically stored as a Java short in a short array, but when the short
 * is stored in a ByteBuffer, it is native endian (as compared to the default Java big endian).
 * The short has full range from [-32768, 32767],
 * and is sometimes interpreted as fixed point Q.15 data.
 * </li>
 * <li> {@link #ENCODING_PCM_FLOAT}: Introduced in
 * API {@link android.os.Build.VERSION_CODES#LOLLIPOP}, this encoding specifies that
 * the audio sample is a 32 bit IEEE single precision float. The sample can be
 * manipulated as a Java float in a float array, though within a ByteBuffer
 * it is stored in native endian byte order.
 * The nominal range of <code>ENCODING_PCM_FLOAT</code> audio data is [-1.0, 1.0].
 * It is implementation dependent whether the positive maximum of 1.0 is included
 * in the interval. Values outside of the nominal range are clamped before
 * sending to the endpoint device. Beware that
 * the handling of NaN is undefined; subnormals may be treated as zero; and
 * infinities are generally clamped just like other values for <code>AudioTrack</code>
 * &ndash; try to avoid infinities because they can easily generate a NaN.
 * <br>
 * To achieve higher audio bit depth than a signed 16 bit integer short,
 * it is recommended to use <code>ENCODING_PCM_FLOAT</code> for audio capture, processing,
 * and playback.
 * Floats are efficiently manipulated by modern CPUs,
 * have greater precision than 24 bit signed integers,
 * and have greater dynamic range than 32 bit signed integers.
 * <code>AudioRecord</code> as of API {@link android.os.Build.VERSION_CODES#M} and
 * <code>AudioTrack</code> as of API {@link android.os.Build.VERSION_CODES#LOLLIPOP}
 * support <code>ENCODING_PCM_FLOAT</code>.
 * </li>
 * </ul>
 * <p>For compressed audio, the encoding specifies the method of compression,
 * for example {@link #ENCODING_AC3} and {@link #ENCODING_DTS}. The compressed
 * audio data is typically stored as bytes in
 * a byte array or ByteBuffer. When a compressed audio encoding is specified
 * for an <code>AudioTrack</code>, it creates a direct (non-mixed) track
 * for output to an endpoint (such as HDMI) capable of decoding the compressed audio.
 * For (most) other endpoints, which are not capable of decoding such compressed audio,
 * you will need to decode the data first, typically by creating a {@link MediaCodec}.
 * Alternatively, one may use {@link MediaPlayer} for playback of compressed
 * audio files or streams.
 * <p>When compressed audio is sent out through a direct <code>AudioTrack</code>,
 * it need not be written in exact multiples of the audio access unit;
 * this differs from <code>MediaCodec</code> input buffers.
 *
 * <h4 id="channelMask">Channel mask</h4>
 * <p>Channel masks are used in <code>AudioTrack</code> and <code>AudioRecord</code> to describe
 * the samples and their arrangement in the audio frame. They are also used in the endpoint (e.g.
 * a USB audio interface, a DAC connected to headphones) to specify allowable configurations of a
 * particular device.
 * <br>As of API {@link android.os.Build.VERSION_CODES#M}, there are two types of channel masks:
 * channel position masks and channel index masks.
 *
 * <h5 id="channelPositionMask">Channel position masks</h5>
 * Channel position masks are the original Android channel masks, and are used since API
 * {@link android.os.Build.VERSION_CODES#BASE}.
 * For input and output, they imply a positional nature - the location of a speaker or a microphone
 * for recording or playback.
 * <br>For a channel position mask, each allowed channel position corresponds to a bit in the
 * channel mask. If that channel position is present in the audio frame, that bit is set,
 * otherwise it is zero. The order of the bits (from lsb to msb) corresponds to the order of that
 * position's sample in the audio frame.
 * <br>The canonical channel position masks by channel count are as follows:
 * <br><table>
 * <tr><td>channel count</td><td>channel position mask</td></tr>
 * <tr><td>1</td><td>{@link #CHANNEL_OUT_MONO}</td></tr>
 * <tr><td>2</td><td>{@link #CHANNEL_OUT_STEREO}</td></tr>
 * <tr><td>3</td><td>{@link #CHANNEL_OUT_STEREO} | {@link #CHANNEL_OUT_FRONT_CENTER}</td></tr>
 * <tr><td>4</td><td>{@link #CHANNEL_OUT_QUAD}</td></tr>
 * <tr><td>5</td><td>{@link #CHANNEL_OUT_QUAD} | {@link #CHANNEL_OUT_FRONT_CENTER}</td></tr>
 * <tr><td>6</td><td>{@link #CHANNEL_OUT_5POINT1}</td></tr>
 * <tr><td>7</td><td>{@link #CHANNEL_OUT_5POINT1} | {@link #CHANNEL_OUT_BACK_CENTER}</td></tr>
 * <tr><td>8</td><td>{@link #CHANNEL_OUT_7POINT1_SURROUND}</td></tr>
 * </table>
 * <br>These masks are an ORed composite of individual channel masks. For example
 * {@link #CHANNEL_OUT_STEREO} is composed of {@link #CHANNEL_OUT_FRONT_LEFT} and
 * {@link #CHANNEL_OUT_FRONT_RIGHT}.
 *
 * <h5 id="channelIndexMask">Channel index masks</h5>
 * Channel index masks are introduced in API {@link android.os.Build.VERSION_CODES#M}. They allow
 * the selection of a particular channel from the source or sink endpoint by number, i.e. the first
 * channel, the second channel, and so forth. This avoids problems with artificially assigning
 * positions to channels of an endpoint, or figuring what the i<sup>th</sup> position bit is within
 * an endpoint's channel position mask etc.
 * <br>Here's an example where channel index masks address this confusion: dealing with a 4 channel
 * USB device. Using a position mask, and based on the channel count, this would be a
 * {@link #CHANNEL_OUT_QUAD} device, but really one is only interested in channel 0
 * through channel 3. The USB device would then have the following individual bit channel masks:
 * {@link #CHANNEL_OUT_FRONT_LEFT},
 * {@link #CHANNEL_OUT_FRONT_RIGHT}, {@link #CHANNEL_OUT_BACK_LEFT}
 * and {@link #CHANNEL_OUT_BACK_RIGHT}. But which is channel 0 and which is
 * channel 3?
 * <br>For a channel index mask, each channel number is represented as a bit in the mask, from the
 * lsb (channel 0) upwards to the msb, numerically this bit value is
 * <code>1 << channelNumber</code>.
 * A set bit indicates that channel is present in the audio frame, otherwise it is cleared.
 * The order of the bits also correspond to that channel number's sample order in the audio frame.
 * <br>For the previous 4 channel USB device example, the device would have a channel index mask
 * <code>0xF</code>. Suppose we wanted to select only the first and the third channels; this would
 * correspond to a channel index mask <code>0x5</code> (the first and third bits set). If an
 * <code>AudioTrack</code> uses this channel index mask, the audio frame would consist of two
 * samples, the first sample of each frame routed to channel 0, and the second sample of each frame
 * routed to channel 2.
 * The canonical channel index masks by channel count are given by the formula
 * <code>(1 << channelCount) - 1</code>.
 *
 * <h5>Use cases</h5>
 * <ul>
 * <li><i>Channel position mask for an endpoint:</i> <code>CHANNEL_OUT_FRONT_LEFT</code>,
 *  <code>CHANNEL_OUT_FRONT_CENTER</code>, etc. for HDMI home theater purposes.
 * <li><i>Channel position mask for an audio stream:</i> Creating an <code>AudioTrack</code>
 *  to output movie content, where 5.1 multichannel output is to be written.
 * <li><i>Channel index mask for an endpoint:</i> USB devices for which input and output do not
 *  correspond to left or right speaker or microphone.
 * <li><i>Channel index mask for an audio stream:</i> An <code>AudioRecord</code> may only want the
 *  third and fourth audio channels of the endpoint (i.e. the second channel pair), and not care the
 *  about position it corresponds to, in which case the channel index mask is <code>0xC</code>.
 *  Multichannel <code>AudioRecord</code> sessions should use channel index masks.
 * </ul>
 * <h4 id="audioFrame">Audio Frame</h4>
 * <p>For linear PCM, an audio frame consists of a set of samples captured at the same time,
 * whose count and
 * channel association are given by the <a href="#channelMask">channel mask</a>,
 * and whose sample contents are specified by the <a href="#encoding">encoding</a>.
 * For example, a stereo 16 bit PCM frame consists of
 * two 16 bit linear PCM samples, with a frame size of 4 bytes.
 * For compressed audio, an audio frame may alternately
 * refer to an access unit of compressed data bytes that is logically grouped together for
 * decoding and bitstream access (e.g. {@link MediaCodec}),
 * or a single byte of compressed data (e.g. {@link AudioTrack#getBufferSizeInFrames()
 * AudioTrack.getBufferSizeInFrames()}),
 * or the linear PCM frame result from decoding the compressed data
 * (e.g.{@link AudioTrack#getPlaybackHeadPosition()
 * AudioTrack.getPlaybackHeadPosition()}),
 * depending on the context where audio frame is used.
 */
public final class AudioFormat implements Parcelable {

    //---------------------------------------------------------
    // Constants
    //--------------------
    /** Invalid audio data format */
    public static final int ENCODING_INVALID = 0;
    /** Default audio data format */
    public static final int ENCODING_DEFAULT = 1;

    // These values must be kept in sync with core/jni/android_media_AudioFormat.h
    // Also sync av/services/audiopolicy/managerdefault/ConfigParsingUtils.h
    /** Audio data format: PCM 16 bit per sample. Guaranteed to be supported by devices. */
    public static final int ENCODING_PCM_16BIT = 2;
    /** Audio data format: PCM 8 bit per sample. Not guaranteed to be supported by devices. */
    public static final int ENCODING_PCM_8BIT = 3;
    /** Audio data format: single-precision floating-point per sample */
    public static final int ENCODING_PCM_FLOAT = 4;
    /** Audio data format: AC-3 compressed */
    public static final int ENCODING_AC3 = 5;
    /** Audio data format: E-AC-3 compressed */
    public static final int ENCODING_E_AC3 = 6;
    /** Audio data format: DTS compressed */
    public static final int ENCODING_DTS = 7;
    /** Audio data format: DTS HD compressed */
    public static final int ENCODING_DTS_HD = 8;
    /** Audio data format: MP3 compressed
     * @hide
     * */
    public static final int ENCODING_MP3 = 9;
    /** Audio data format: AAC LC compressed
     * @hide
     * */
    public static final int ENCODING_AAC_LC = 10;
    /** Audio data format: AAC HE V1 compressed
     * @hide
     * */
    public static final int ENCODING_AAC_HE_V1 = 11;
    /** Audio data format: AAC HE V2 compressed
     * @hide
     * */
    public static final int ENCODING_AAC_HE_V2 = 12;
    /** Audio data format: compressed audio wrapped in PCM for HDMI
     * or S/PDIF passthrough.
     * IEC61937 uses a stereo stream of 16-bit samples as the wrapper.
     * So the channel mask for the track must be {@link #CHANNEL_OUT_STEREO}.
     * Data should be written to the stream in a short[] array.
     * If the data is written in a byte[] array then there may be endian problems
     * on some platforms when converting to short internally.
     */
    public static final int ENCODING_IEC61937 = 13;
    /** Audio data format: DOLBY TRUEHD compressed
     **/
    public static final int ENCODING_DOLBY_TRUEHD = 14;

    /** Invalid audio channel configuration */
    /** @deprecated Use {@link #CHANNEL_INVALID} instead.  */
    @Deprecated    public static final int CHANNEL_CONFIGURATION_INVALID   = 0;
    /** Default audio channel configuration */
    /** @deprecated Use {@link #CHANNEL_OUT_DEFAULT} or {@link #CHANNEL_IN_DEFAULT} instead.  */
    @Deprecated    public static final int CHANNEL_CONFIGURATION_DEFAULT   = 1;
    /** Mono audio configuration */
    /** @deprecated Use {@link #CHANNEL_OUT_MONO} or {@link #CHANNEL_IN_MONO} instead.  */
    @Deprecated    public static final int CHANNEL_CONFIGURATION_MONO      = 2;
    /** Stereo (2 channel) audio configuration */
    /** @deprecated Use {@link #CHANNEL_OUT_STEREO} or {@link #CHANNEL_IN_STEREO} instead.  */
    @Deprecated    public static final int CHANNEL_CONFIGURATION_STEREO    = 3;

    /** Invalid audio channel mask */
    public static final int CHANNEL_INVALID = 0;
    /** Default audio channel mask */
    public static final int CHANNEL_OUT_DEFAULT = 1;

    // Output channel mask definitions below are translated to the native values defined in
    //  in /system/media/audio/include/system/audio.h in the JNI code of AudioTrack
    public static final int CHANNEL_OUT_FRONT_LEFT = 0x4;
    public static final int CHANNEL_OUT_FRONT_RIGHT = 0x8;
    public static final int CHANNEL_OUT_FRONT_CENTER = 0x10;
    public static final int CHANNEL_OUT_LOW_FREQUENCY = 0x20;
    public static final int CHANNEL_OUT_BACK_LEFT = 0x40;
    public static final int CHANNEL_OUT_BACK_RIGHT = 0x80;
    public static final int CHANNEL_OUT_FRONT_LEFT_OF_CENTER = 0x100;
    public static final int CHANNEL_OUT_FRONT_RIGHT_OF_CENTER = 0x200;
    public static final int CHANNEL_OUT_BACK_CENTER = 0x400;
    public static final int CHANNEL_OUT_SIDE_LEFT =         0x800;
    public static final int CHANNEL_OUT_SIDE_RIGHT =       0x1000;
    /** @hide */
    public static final int CHANNEL_OUT_TOP_CENTER =       0x2000;
    /** @hide */
    public static final int CHANNEL_OUT_TOP_FRONT_LEFT =   0x4000;
    /** @hide */
    public static final int CHANNEL_OUT_TOP_FRONT_CENTER = 0x8000;
    /** @hide */
    public static final int CHANNEL_OUT_TOP_FRONT_RIGHT = 0x10000;
    /** @hide */
    public static final int CHANNEL_OUT_TOP_BACK_LEFT =   0x20000;
    /** @hide */
    public static final int CHANNEL_OUT_TOP_BACK_CENTER = 0x40000;
    /** @hide */
    public static final int CHANNEL_OUT_TOP_BACK_RIGHT =  0x80000;

    public static final int CHANNEL_OUT_MONO = CHANNEL_OUT_FRONT_LEFT;
    public static final int CHANNEL_OUT_STEREO = (CHANNEL_OUT_FRONT_LEFT | CHANNEL_OUT_FRONT_RIGHT);
    // aka QUAD_BACK
    public static final int CHANNEL_OUT_QUAD = (CHANNEL_OUT_FRONT_LEFT | CHANNEL_OUT_FRONT_RIGHT |
            CHANNEL_OUT_BACK_LEFT | CHANNEL_OUT_BACK_RIGHT);
    /** @hide */
    public static final int CHANNEL_OUT_QUAD_SIDE = (CHANNEL_OUT_FRONT_LEFT | CHANNEL_OUT_FRONT_RIGHT |
            CHANNEL_OUT_SIDE_LEFT | CHANNEL_OUT_SIDE_RIGHT);
    public static final int CHANNEL_OUT_SURROUND = (CHANNEL_OUT_FRONT_LEFT | CHANNEL_OUT_FRONT_RIGHT |
            CHANNEL_OUT_FRONT_CENTER | CHANNEL_OUT_BACK_CENTER);
    // aka 5POINT1_BACK
    public static final int CHANNEL_OUT_5POINT1 = (CHANNEL_OUT_FRONT_LEFT | CHANNEL_OUT_FRONT_RIGHT |
            CHANNEL_OUT_FRONT_CENTER | CHANNEL_OUT_LOW_FREQUENCY | CHANNEL_OUT_BACK_LEFT | CHANNEL_OUT_BACK_RIGHT);
    /** @hide */
    public static final int CHANNEL_OUT_5POINT1_SIDE = (CHANNEL_OUT_FRONT_LEFT | CHANNEL_OUT_FRONT_RIGHT |
            CHANNEL_OUT_FRONT_CENTER | CHANNEL_OUT_LOW_FREQUENCY |
            CHANNEL_OUT_SIDE_LEFT | CHANNEL_OUT_SIDE_RIGHT);
    // different from AUDIO_CHANNEL_OUT_7POINT1 used internally, and not accepted by AudioRecord.
    /** @deprecated Not the typical 7.1 surround configuration. Use {@link #CHANNEL_OUT_7POINT1_SURROUND} instead. */
    @Deprecated    public static final int CHANNEL_OUT_7POINT1 = (CHANNEL_OUT_FRONT_LEFT | CHANNEL_OUT_FRONT_RIGHT |
            CHANNEL_OUT_FRONT_CENTER | CHANNEL_OUT_LOW_FREQUENCY | CHANNEL_OUT_BACK_LEFT | CHANNEL_OUT_BACK_RIGHT |
            CHANNEL_OUT_FRONT_LEFT_OF_CENTER | CHANNEL_OUT_FRONT_RIGHT_OF_CENTER);
    // matches AUDIO_CHANNEL_OUT_7POINT1
    public static final int CHANNEL_OUT_7POINT1_SURROUND = (
            CHANNEL_OUT_FRONT_LEFT | CHANNEL_OUT_FRONT_CENTER | CHANNEL_OUT_FRONT_RIGHT |
            CHANNEL_OUT_SIDE_LEFT | CHANNEL_OUT_SIDE_RIGHT |
            CHANNEL_OUT_BACK_LEFT | CHANNEL_OUT_BACK_RIGHT |
            CHANNEL_OUT_LOW_FREQUENCY);
    // CHANNEL_OUT_ALL is not yet defined; if added then it should match AUDIO_CHANNEL_OUT_ALL

    /** Minimum value for sample rate,
     *  assuming AudioTrack and AudioRecord share the same limitations.
     * @hide
     */
    // never unhide
    public static final int SAMPLE_RATE_HZ_MIN = 4000;
    /** Maximum value for sample rate,
     *  assuming AudioTrack and AudioRecord share the same limitations.
     * @hide
     */
    // never unhide
    public static final int SAMPLE_RATE_HZ_MAX = 192000;
    /** Sample rate will be a route-dependent value.
     * For AudioTrack, it is usually the sink sample rate,
     * and for AudioRecord it is usually the source sample rate.
     */
    public static final int SAMPLE_RATE_UNSPECIFIED = 0;

    /**
     * @hide
     * Return the input channel mask corresponding to an output channel mask.
     * This can be used for submix rerouting for the mask of the recorder to map to that of the mix.
     * @param outMask a combination of the CHANNEL_OUT_* definitions, but not CHANNEL_OUT_DEFAULT
     * @return a combination of CHANNEL_IN_* definitions matching an output channel mask
     * @throws IllegalArgumentException
     */
    public static int inChannelMaskFromOutChannelMask(int outMask) throws IllegalArgumentException {
        if (outMask == CHANNEL_OUT_DEFAULT) {
            throw new IllegalArgumentException(
                    "Illegal CHANNEL_OUT_DEFAULT channel mask for input.");
        }
        switch (channelCountFromOutChannelMask(outMask)) {
            case 1:
                return CHANNEL_IN_MONO;
            case 2:
                return CHANNEL_IN_STEREO;
            default:
                throw new IllegalArgumentException("Unsupported channel configuration for input.");
        }
    }

    /**
     * @hide
     * Return the number of channels from an input channel mask
     * @param mask a combination of the CHANNEL_IN_* definitions, even CHANNEL_IN_DEFAULT
     * @return number of channels for the mask
     */
    public static int channelCountFromInChannelMask(int mask) {
        return Integer.bitCount(mask);
    }
    /**
     * @hide
     * Return the number of channels from an output channel mask
     * @param mask a combination of the CHANNEL_OUT_* definitions, but not CHANNEL_OUT_DEFAULT
     * @return number of channels for the mask
     */
    public static int channelCountFromOutChannelMask(int mask) {
        return Integer.bitCount(mask);
    }
    /**
     * @hide
     * Return a channel mask ready to be used by native code
     * @param mask a combination of the CHANNEL_OUT_* definitions, but not CHANNEL_OUT_DEFAULT
     * @return a native channel mask
     */
    public static int convertChannelOutMaskToNativeMask(int javaMask) {
        return (javaMask >> 2);
    }

    /**
     * @hide
     * Return a java output channel mask
     * @param mask a native channel mask
     * @return a combination of the CHANNEL_OUT_* definitions
     */
    public static int convertNativeChannelMaskToOutMask(int nativeMask) {
        return (nativeMask << 2);
    }

    public static final int CHANNEL_IN_DEFAULT = 1;
    // These directly match native
    public static final int CHANNEL_IN_LEFT = 0x4;
    public static final int CHANNEL_IN_RIGHT = 0x8;
    public static final int CHANNEL_IN_FRONT = 0x10;
    public static final int CHANNEL_IN_BACK = 0x20;
    public static final int CHANNEL_IN_LEFT_PROCESSED = 0x40;
    public static final int CHANNEL_IN_RIGHT_PROCESSED = 0x80;
    public static final int CHANNEL_IN_FRONT_PROCESSED = 0x100;
    public static final int CHANNEL_IN_BACK_PROCESSED = 0x200;
    public static final int CHANNEL_IN_PRESSURE = 0x400;
    public static final int CHANNEL_IN_X_AXIS = 0x800;
    public static final int CHANNEL_IN_Y_AXIS = 0x1000;
    public static final int CHANNEL_IN_Z_AXIS = 0x2000;
    public static final int CHANNEL_IN_VOICE_UPLINK = 0x4000;
    public static final int CHANNEL_IN_VOICE_DNLINK = 0x8000;
    public static final int CHANNEL_IN_MONO = CHANNEL_IN_FRONT;
    public static final int CHANNEL_IN_STEREO = (CHANNEL_IN_LEFT | CHANNEL_IN_RIGHT);
    /** @hide */
    public static final int CHANNEL_IN_FRONT_BACK = CHANNEL_IN_FRONT | CHANNEL_IN_BACK;
    // CHANNEL_IN_ALL is not yet defined; if added then it should match AUDIO_CHANNEL_IN_ALL

    /** @hide */
    public static int getBytesPerSample(int audioFormat)
    {
        switch (audioFormat) {
        case ENCODING_PCM_8BIT:
            return 1;
        case ENCODING_PCM_16BIT:
        case ENCODING_IEC61937:
        case ENCODING_DEFAULT:
            return 2;
        case ENCODING_PCM_FLOAT:
            return 4;
        case ENCODING_INVALID:
        default:
            throw new IllegalArgumentException("Bad audio format " + audioFormat);
        }
    }

    /** @hide */
    public static boolean isValidEncoding(int audioFormat)
    {
        switch (audioFormat) {
        case ENCODING_PCM_8BIT:
        case ENCODING_PCM_16BIT:
        case ENCODING_PCM_FLOAT:
        case ENCODING_AC3:
        case ENCODING_E_AC3:
        case ENCODING_DTS:
        case ENCODING_DTS_HD:
        case ENCODING_MP3:
        case ENCODING_AAC_LC:
        case ENCODING_AAC_HE_V1:
        case ENCODING_AAC_HE_V2:
        case ENCODING_IEC61937:
            return true;
        default:
            return false;
        }
    }

    /** @hide */
    public static boolean isPublicEncoding(int audioFormat)
    {
        switch (audioFormat) {
        case ENCODING_PCM_8BIT:
        case ENCODING_PCM_16BIT:
        case ENCODING_PCM_FLOAT:
        case ENCODING_AC3:
        case ENCODING_E_AC3:
        case ENCODING_DTS:
        case ENCODING_DTS_HD:
        case ENCODING_IEC61937:
            return true;
        default:
            return false;
        }
    }

    /** @hide */
    public static boolean isEncodingLinearPcm(int audioFormat)
    {
        switch (audioFormat) {
        case ENCODING_PCM_8BIT:
        case ENCODING_PCM_16BIT:
        case ENCODING_PCM_FLOAT:
        case ENCODING_DEFAULT:
            return true;
        case ENCODING_AC3:
        case ENCODING_E_AC3:
        case ENCODING_DTS:
        case ENCODING_DTS_HD:
        case ENCODING_MP3:
        case ENCODING_AAC_LC:
        case ENCODING_AAC_HE_V1:
        case ENCODING_AAC_HE_V2:
        case ENCODING_IEC61937: // wrapped in PCM but compressed
            return false;
        case ENCODING_INVALID:
        default:
            throw new IllegalArgumentException("Bad audio format " + audioFormat);
        }
    }

    /** @hide */
    public static boolean isEncodingLinearFrames(int audioFormat)
    {
        switch (audioFormat) {
        case ENCODING_PCM_8BIT:
        case ENCODING_PCM_16BIT:
        case ENCODING_PCM_FLOAT:
        case ENCODING_IEC61937: // same size as stereo PCM
        case ENCODING_DEFAULT:
            return true;
        case ENCODING_AC3:
        case ENCODING_E_AC3:
        case ENCODING_DTS:
        case ENCODING_DTS_HD:
        case ENCODING_MP3:
        case ENCODING_AAC_LC:
        case ENCODING_AAC_HE_V1:
        case ENCODING_AAC_HE_V2:
            return false;
        case ENCODING_INVALID:
        default:
            throw new IllegalArgumentException("Bad audio format " + audioFormat);
        }
    }
    /**
     * Returns an array of public encoding values extracted from an array of
     * encoding values.
     * @hide
     */
    public static int[] filterPublicFormats(int[] formats) {
        if (formats == null) {
            return null;
        }
        int[] myCopy = Arrays.copyOf(formats, formats.length);
        int size = 0;
        for (int i = 0; i < myCopy.length; i++) {
            if (isPublicEncoding(myCopy[i])) {
                if (size != i) {
                    myCopy[size] = myCopy[i];
                }
                size++;
            }
        }
        return Arrays.copyOf(myCopy, size);
    }

    /** @removed */
    public AudioFormat()
    {
        throw new UnsupportedOperationException("There is no valid usage of this constructor");
    }

    /**
     * Private constructor with an ignored argument to differentiate from the removed default ctor
     * @param ignoredArgument
     */
    private AudioFormat(int ignoredArgument) {
    }

    /**
     * Constructor used by the JNI.  Parameters are not checked for validity.
     */
    // Update sound trigger JNI in core/jni/android_hardware_SoundTrigger.cpp when modifying this
    // constructor
    private AudioFormat(int encoding, int sampleRate, int channelMask, int channelIndexMask) {
        mEncoding = encoding;
        mSampleRate = sampleRate;
        mChannelMask = channelMask;
        mChannelIndexMask = channelIndexMask;
        mPropertySetMask = AUDIO_FORMAT_HAS_PROPERTY_ENCODING |
                AUDIO_FORMAT_HAS_PROPERTY_SAMPLE_RATE |
                AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_MASK |
                AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_INDEX_MASK;
    }

    /** @hide */
    public final static int AUDIO_FORMAT_HAS_PROPERTY_NONE = 0x0;
    /** @hide */
    public final static int AUDIO_FORMAT_HAS_PROPERTY_ENCODING = 0x1 << 0;
    /** @hide */
    public final static int AUDIO_FORMAT_HAS_PROPERTY_SAMPLE_RATE = 0x1 << 1;
    /** @hide */
    public final static int AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_MASK = 0x1 << 2;
    /** @hide */
    public final static int AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_INDEX_MASK = 0x1 << 3;

    private int mEncoding;
    private int mSampleRate;
    private int mChannelMask;
    private int mChannelIndexMask;
    private int mPropertySetMask;

    /**
     * Return the encoding.
     * See the section on <a href="#encoding">encodings</a> for more information about the different
     * types of supported audio encoding.
     * @return one of the values that can be set in {@link Builder#setEncoding(int)} or
     * {@link AudioFormat#ENCODING_INVALID} if not set.
     */
    public int getEncoding() {
        if ((mPropertySetMask & AUDIO_FORMAT_HAS_PROPERTY_ENCODING) == 0) {
            return ENCODING_INVALID;
        }
        return mEncoding;
    }

    /**
     * Return the sample rate.
     * @return one of the values that can be set in {@link Builder#setSampleRate(int)} or
     * {@link #SAMPLE_RATE_UNSPECIFIED} if not set.
     */
    public int getSampleRate() {
        return mSampleRate;
    }

    /**
     * Return the channel mask.
     * See the section on <a href="#channelMask">channel masks</a> for more information about
     * the difference between index-based masks(as returned by {@link #getChannelIndexMask()}) and
     * the position-based mask returned by this function.
     * @return one of the values that can be set in {@link Builder#setChannelMask(int)} or
     * {@link AudioFormat#CHANNEL_INVALID} if not set.
     */
    public int getChannelMask() {
        if ((mPropertySetMask & AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_MASK) == 0) {
            return CHANNEL_INVALID;
        }
        return mChannelMask;
    }

    /**
     * Return the channel index mask.
     * See the section on <a href="#channelMask">channel masks</a> for more information about
     * the difference between index-based masks, and position-based masks (as returned
     * by {@link #getChannelMask()}).
     * @return one of the values that can be set in {@link Builder#setChannelIndexMask(int)} or
     * {@link AudioFormat#CHANNEL_INVALID} if not set or an invalid mask was used.
     */
    public int getChannelIndexMask() {
        if ((mPropertySetMask & AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_INDEX_MASK) == 0) {
            return CHANNEL_INVALID;
        }
        return mChannelIndexMask;
    }

    /**
     * Return the channel count.
     * @return the channel count derived from the channel position mask or the channel index mask.
     * Zero is returned if both the channel position mask and the channel index mask are not set.
     */
    public int getChannelCount() {
        final int channelIndexCount = Integer.bitCount(getChannelIndexMask());
        int channelCount = channelCountFromOutChannelMask(getChannelMask());
        if (channelCount == 0) {
            channelCount = channelIndexCount;
        } else if (channelCount != channelIndexCount && channelIndexCount != 0) {
            channelCount = 0; // position and index channel count mismatch
        }
        return channelCount;
    }

    /** @hide */
    public int getPropertySetMask() {
        return mPropertySetMask;
    }

    /**
     * Builder class for {@link AudioFormat} objects.
     * Use this class to configure and create an AudioFormat instance. By setting format
     * characteristics such as audio encoding, channel mask or sample rate, you indicate which
     * of those are to vary from the default behavior on this device wherever this audio format
     * is used. See {@link AudioFormat} for a complete description of the different parameters that
     * can be used to configure an <code>AudioFormat</code> instance.
     * <p>{@link AudioFormat} is for instance used in
     * {@link AudioTrack#AudioTrack(AudioAttributes, AudioFormat, int, int, int)}. In this
     * constructor, every format characteristic set on the <code>Builder</code> (e.g. with
     * {@link #setSampleRate(int)}) will alter the default values used by an
     * <code>AudioTrack</code>. In this case for audio playback with <code>AudioTrack</code>, the
     * sample rate set in the <code>Builder</code> would override the platform output sample rate
     * which would otherwise be selected by default.
     */
    public static class Builder {
        private int mEncoding = ENCODING_INVALID;
        private int mSampleRate = SAMPLE_RATE_UNSPECIFIED;
        private int mChannelMask = CHANNEL_INVALID;
        private int mChannelIndexMask = 0;
        private int mPropertySetMask = AUDIO_FORMAT_HAS_PROPERTY_NONE;

        /**
         * Constructs a new Builder with none of the format characteristics set.
         */
        public Builder() {
        }

        /**
         * Constructs a new Builder from a given {@link AudioFormat}.
         * @param af the {@link AudioFormat} object whose data will be reused in the new Builder.
         */
        public Builder(AudioFormat af) {
            mEncoding = af.mEncoding;
            mSampleRate = af.mSampleRate;
            mChannelMask = af.mChannelMask;
            mChannelIndexMask = af.mChannelIndexMask;
            mPropertySetMask = af.mPropertySetMask;
        }

        /**
         * Combines all of the format characteristics that have been set and return a new
         * {@link AudioFormat} object.
         * @return a new {@link AudioFormat} object
         */
        public AudioFormat build() {
            AudioFormat af = new AudioFormat(1980/*ignored*/);
            af.mEncoding = mEncoding;
            // not calling setSampleRate is equivalent to calling
            // setSampleRate(SAMPLE_RATE_UNSPECIFIED)
            af.mSampleRate = mSampleRate;
            af.mChannelMask = mChannelMask;
            af.mChannelIndexMask = mChannelIndexMask;
            af.mPropertySetMask = mPropertySetMask;
            return af;
        }

        /**
         * Sets the data encoding format.
         * @param encoding one of {@link AudioFormat#ENCODING_DEFAULT},
         *     {@link AudioFormat#ENCODING_PCM_8BIT},
         *     {@link AudioFormat#ENCODING_PCM_16BIT},
         *     {@link AudioFormat#ENCODING_PCM_FLOAT},
         *     {@link AudioFormat#ENCODING_AC3},
         *     {@link AudioFormat#ENCODING_E_AC3}.
         *     {@link AudioFormat#ENCODING_DTS},
         *     {@link AudioFormat#ENCODING_DTS_HD}.
         * @return the same Builder instance.
         * @throws java.lang.IllegalArgumentException
         */
        public Builder setEncoding(@Encoding int encoding) throws IllegalArgumentException {
            switch (encoding) {
                case ENCODING_DEFAULT:
                    mEncoding = ENCODING_PCM_16BIT;
                    break;
                case ENCODING_PCM_8BIT:
                case ENCODING_PCM_16BIT:
                case ENCODING_PCM_FLOAT:
                case ENCODING_AC3:
                case ENCODING_E_AC3:
                case ENCODING_DTS:
                case ENCODING_DTS_HD:
                case ENCODING_IEC61937:
                    mEncoding = encoding;
                    break;
                case ENCODING_INVALID:
                default:
                    throw new IllegalArgumentException("Invalid encoding " + encoding);
            }
            mPropertySetMask |= AUDIO_FORMAT_HAS_PROPERTY_ENCODING;
            return this;
        }

        /**
         * Sets the channel position mask.
         * The channel position mask specifies the association between audio samples in a frame
         * with named endpoint channels. The samples in the frame correspond to the
         * named set bits in the channel position mask, in ascending bit order.
         * See {@link #setChannelIndexMask(int)} to specify channels
         * based on endpoint numbered channels. This <a href="#channelPositionMask>description of
         * channel position masks</a> covers the concept in more details.
         * @param channelMask describes the configuration of the audio channels.
         *    <p> For output, the channelMask can be an OR-ed combination of
         *    channel position masks, e.g.
         *    {@link AudioFormat#CHANNEL_OUT_FRONT_LEFT},
         *    {@link AudioFormat#CHANNEL_OUT_FRONT_RIGHT},
         *    {@link AudioFormat#CHANNEL_OUT_FRONT_CENTER},
         *    {@link AudioFormat#CHANNEL_OUT_LOW_FREQUENCY}
         *    {@link AudioFormat#CHANNEL_OUT_BACK_LEFT},
         *    {@link AudioFormat#CHANNEL_OUT_BACK_RIGHT},
         *    {@link AudioFormat#CHANNEL_OUT_BACK_CENTER},
         *    {@link AudioFormat#CHANNEL_OUT_SIDE_LEFT},
         *    {@link AudioFormat#CHANNEL_OUT_SIDE_RIGHT}.
         *    <p> For a valid {@link AudioTrack} channel position mask,
         *    the following conditions apply:
         *    <br> (1) at most eight channel positions may be used;
         *    <br> (2) right/left pairs should be matched.
         *    <p> For input or {@link AudioRecord}, the mask should be
         *    {@link AudioFormat#CHANNEL_IN_MONO} or
         *    {@link AudioFormat#CHANNEL_IN_STEREO}.  {@link AudioFormat#CHANNEL_IN_MONO} is
         *    guaranteed to work on all devices.
         * @return the same <code>Builder</code> instance.
         * @throws IllegalArgumentException if the channel mask is invalid or
         *    if both channel index mask and channel position mask
         *    are specified but do not have the same channel count.
         */
        public @NonNull Builder setChannelMask(int channelMask) {
            if (channelMask == CHANNEL_INVALID) {
                throw new IllegalArgumentException("Invalid zero channel mask");
            } else if (/* channelMask != 0 && */ mChannelIndexMask != 0 &&
                    Integer.bitCount(channelMask) != Integer.bitCount(mChannelIndexMask)) {
                throw new IllegalArgumentException("Mismatched channel count for mask " +
                        Integer.toHexString(channelMask).toUpperCase());
            }
            mChannelMask = channelMask;
            mPropertySetMask |= AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_MASK;
            return this;
        }

        /**
         * Sets the channel index mask.
         * A channel index mask specifies the association of audio samples in the frame
         * with numbered endpoint channels. The i-th bit in the channel index
         * mask corresponds to the i-th endpoint channel.
         * For example, an endpoint with four channels is represented
         * as index mask bits 0 through 3. This <a href="#channelIndexMask>description of channel
         * index masks</a> covers the concept in more details.
         * See {@link #setChannelMask(int)} for a positional mask interpretation.
         * <p> Both {@link AudioTrack} and {@link AudioRecord} support
         * a channel index mask.
         * If a channel index mask is specified it is used,
         * otherwise the channel position mask specified
         * by <code>setChannelMask</code> is used.
         * For <code>AudioTrack</code> and <code>AudioRecord</code>,
         * a channel position mask is not required if a channel index mask is specified.
         *
         * @param channelIndexMask describes the configuration of the audio channels.
         *    <p> For output, the <code>channelIndexMask</code> is an OR-ed combination of
         *    bits representing the mapping of <code>AudioTrack</code> write samples
         *    to output sink channels.
         *    For example, a mask of <code>0xa</code>, or binary <code>1010</code>,
         *    means the <code>AudioTrack</code> write frame consists of two samples,
         *    which are routed to the second and the fourth channels of the output sink.
         *    Unmatched output sink channels are zero filled and unmatched
         *    <code>AudioTrack</code> write samples are dropped.
         *    <p> For input, the <code>channelIndexMask</code> is an OR-ed combination of
         *    bits representing the mapping of input source channels to
         *    <code>AudioRecord</code> read samples.
         *    For example, a mask of <code>0x5</code>, or binary
         *    <code>101</code>, will read from the first and third channel of the input
         *    source device and store them in the first and second sample of the
         *    <code>AudioRecord</code> read frame.
         *    Unmatched input source channels are dropped and
         *    unmatched <code>AudioRecord</code> read samples are zero filled.
         * @return the same <code>Builder</code> instance.
         * @throws IllegalArgumentException if the channel index mask is invalid or
         *    if both channel index mask and channel position mask
         *    are specified but do not have the same channel count.
         */
        public @NonNull Builder setChannelIndexMask(int channelIndexMask) {
            if (channelIndexMask == 0) {
                throw new IllegalArgumentException("Invalid zero channel index mask");
            } else if (/* channelIndexMask != 0 && */ mChannelMask != 0 &&
                    Integer.bitCount(channelIndexMask) != Integer.bitCount(mChannelMask)) {
                throw new IllegalArgumentException("Mismatched channel count for index mask " +
                        Integer.toHexString(channelIndexMask).toUpperCase());
            }
            mChannelIndexMask = channelIndexMask;
            mPropertySetMask |= AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_INDEX_MASK;
            return this;
        }

        /**
         * Sets the sample rate.
         * @param sampleRate the sample rate expressed in Hz
         * @return the same Builder instance.
         * @throws java.lang.IllegalArgumentException
         */
        public Builder setSampleRate(int sampleRate) throws IllegalArgumentException {
            // TODO Consider whether to keep the MIN and MAX range checks here.
            // It is not necessary and poses the problem of defining the limits independently from
            // native implementation or platform capabilities.
            if (((sampleRate < SAMPLE_RATE_HZ_MIN) || (sampleRate > SAMPLE_RATE_HZ_MAX)) &&
                    sampleRate != SAMPLE_RATE_UNSPECIFIED) {
                throw new IllegalArgumentException("Invalid sample rate " + sampleRate);
            }
            mSampleRate = sampleRate;
            mPropertySetMask |= AUDIO_FORMAT_HAS_PROPERTY_SAMPLE_RATE;
            return this;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AudioFormat that = (AudioFormat) o;

        if (mPropertySetMask != that.mPropertySetMask) return false;

        // return false if any of the properties is set and the values differ
        return !((((mPropertySetMask & AUDIO_FORMAT_HAS_PROPERTY_ENCODING) != 0)
                            && (mEncoding != that.mEncoding))
                    || (((mPropertySetMask & AUDIO_FORMAT_HAS_PROPERTY_SAMPLE_RATE) != 0)
                            && (mSampleRate != that.mSampleRate))
                    || (((mPropertySetMask & AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_MASK) != 0)
                            && (mChannelMask != that.mChannelMask))
                    || (((mPropertySetMask & AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_INDEX_MASK) != 0)
                            && (mChannelIndexMask != that.mChannelIndexMask)));
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPropertySetMask, mSampleRate, mEncoding, mChannelMask,
                mChannelIndexMask);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPropertySetMask);
        dest.writeInt(mEncoding);
        dest.writeInt(mSampleRate);
        dest.writeInt(mChannelMask);
        dest.writeInt(mChannelIndexMask);
    }

    private AudioFormat(Parcel in) {
        mPropertySetMask = in.readInt();
        mEncoding = in.readInt();
        mSampleRate = in.readInt();
        mChannelMask = in.readInt();
        mChannelIndexMask = in.readInt();
    }

    public static final Parcelable.Creator<AudioFormat> CREATOR =
            new Parcelable.Creator<AudioFormat>() {
        public AudioFormat createFromParcel(Parcel p) {
            return new AudioFormat(p);
        }
        public AudioFormat[] newArray(int size) {
            return new AudioFormat[size];
        }
    };

    @Override
    public String toString () {
        return new String("AudioFormat:"
                + " props=" + mPropertySetMask
                + " enc=" + mEncoding
                + " chan=0x" + Integer.toHexString(mChannelMask).toUpperCase()
                + " chan_index=0x" + Integer.toHexString(mChannelIndexMask).toUpperCase()
                + " rate=" + mSampleRate);
    }

    /** @hide */
    @IntDef({
        ENCODING_DEFAULT,
        ENCODING_PCM_8BIT,
        ENCODING_PCM_16BIT,
        ENCODING_PCM_FLOAT,
        ENCODING_AC3,
        ENCODING_E_AC3,
        ENCODING_DTS,
        ENCODING_DTS_HD,
        ENCODING_IEC61937
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Encoding {}

}
