package com.dariancabot.scope.device.protek506;

import com.dariancabot.scope.Data;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * The Decoder class is used to decode data packets from a Protek 506 DMM and update a provided {@link Data} Object with the aquired data.
 *
 * @author Darian Cabot
 */
public final class Decoder
{
    private final Data data;
    private EventListener eventListener;

    private static final byte packetEndByte = 0x0d; // ASCII code 13 (CR).


    //-----------------------------------------------------------------------
    /**
     * Constructor.
     *
     * @param data the Data object to be used
     */
    public Decoder(Data data)
    {
        this.data = data;
    }


    //-----------------------------------------------------------------------
    /**
     * Decodes a Protek 506 packet, updates the Data object, and notifyies when complete using the EventListener.
     *
     * @param buffer The packet as a byte array. Must be 43 bytes long.
     *
     * @throws ProtocolException If the packet is invalid or unable to decode.
     */
    public void decodeSerialData(byte[] buffer) throws ProtocolException
    {
        // Check packet length.
        if (buffer.length != 43)
        {
            ProtocolException ex = new ProtocolException("Decode error: Packet length is " + buffer.length + ", but should be 43.");
            throw ex;
        }

        // Check for end byte of packet.
        if (buffer[42] != packetEndByte)
        {
            ProtocolException ex = new ProtocolException("Decode error: Packet end byte 0x5d not found at end of packet.");
            throw ex;
        }

        data.packetRaw = buffer; // Set the raw packet value.

        // Correct bit order of buffer and remove overhead (blank nibbles) to make workable packet data.
        ArrayList<Byte> packetBytes = new ArrayList<>();
        int byteCount = 0;
        byte lastByte = 0;

        for (int i = 0; i < buffer.length; i ++)
        {
            if (buffer[i] == packetEndByte) // End of packet.
            {

                byte[] array = new byte[packetBytes.size()];
                int j = 0;
                for (Byte current : packetBytes)
                {
                    array[j] = current;
                    j ++;
                }

                String packet;

                try
                {
                    packet = new String(array, "US-ASCII");
                }
                catch (UnsupportedEncodingException ex)
                {
                    throw new ProtocolException("Decode error: Unable to convert packet to ASCII", ex);
                }

                data.packetTidy = array; // Set the tidy packet value.
                decodePacket(packet); // Decode the packet.
                return;
            }
            else
            {
                packetBytes.add(buffer[i]);
            }
        }
    }


    /**
     * Checks if a byte's bits match a mask.
     *
     * @param data The byte to check
     * @param mask The bit mask
     *
     * @return true if data's bits match the mask, otherwise false
     */
    private boolean checkMask(byte data, byte mask)
    {
        return (data & mask) == mask;
    }


    //-----------------------------------------------------------------------
    /**
     * Decodes a complete serial packet from the Protek 506 DMM. The decoded data will populate the provided Data object.
     *
     * @param packet
     *
     */
    private void decodePacket(String packet)
    {
        packet = packet.trim();

        String[] sections = packet.split(" ");

        //
        // Set main value.
        data.value[0].setValue(sections[1]);

        // Main value unit prefix.
        data.mainValue.unit.setPrefix(Data.Value.Unit.Prefix.NONE);

        if (checkMask(packet[14], BitMask.MAIN_KILO))
        {
            data.mainValue.unit.setPrefix(Data.Value.Unit.Prefix.KILO);
        }

        if (checkMask(packet[14], BitMask.MAIN_MEGA))
        {
            data.mainValue.unit.setPrefix(Data.Value.Unit.Prefix.MEGA);
        }

        if (checkMask(packet[14], BitMask.MAIN_MICRO))
        {
            data.mainValue.unit.setPrefix(Data.Value.Unit.Prefix.MICRO);
        }

        if (checkMask(packet[14], BitMask.MAIN_MILLI))
        {
            data.mainValue.unit.setPrefix(Data.Value.Unit.Prefix.MILLI);
        }

        if (checkMask(packet[14], BitMask.MAIN_NANO))
        {
            data.mainValue.unit.setPrefix(Data.Value.Unit.Prefix.NANO);
        }

        // Main value unit measurement.
        data.mainValue.unit.setMeasurement(Data.Value.Unit.Measurement.NONE);

        if (checkMask(packet[13], BitMask.MAIN_HERTZ))
        {
            data.mainValue.unit.setMeasurement(Data.Value.Unit.Measurement.HERTZ);
        }

        if (checkMask(packet[13], BitMask.MAIN_DEG_F))
        {
            data.mainValue.unit.setMeasurement(Data.Value.Unit.Measurement.DEG_F);
        }

        // The (lowercase) 's' unit is on the DMM's LCD, however I haven't seen it used and I don't know it's meaning.
        // It's included here so the packet is completly decoded, but it's meaning is unknown. Can anyone enlighten me?
        if (checkMask(packet[13], BitMask.MAIN_S_SM))
        {
            data.mainValue.unit.setMeasurement(Data.Value.Unit.Measurement.S);
        }

        if (checkMask(packet[13], BitMask.MAIN_OHM))
        {
            data.mainValue.unit.setMeasurement(Data.Value.Unit.Measurement.OHM);
        }

        if (checkMask(packet[13], BitMask.MAIN_AMP))
        {
            data.mainValue.unit.setMeasurement(Data.Value.Unit.Measurement.AMPERE);
        }

        if (checkMask(packet[13], BitMask.MAIN_FARAD))
        {
            data.mainValue.unit.setMeasurement(Data.Value.Unit.Measurement.FARAD);
        }

        if (checkMask(packet[14], BitMask.MAIN_VOLT))
        {
            data.mainValue.unit.setMeasurement(Data.Value.Unit.Measurement.VOLT);
        }

        if (checkMask(packet[14], BitMask.MAIN_S_LG))
        {
            // There are two units that use the same 'S' symbol on the display.
            // Determine the correct one by checking for pulse width (seconds).

            if (checkMask(packet[4], BitMask.MAIN_PW))
            {
                // Pulse width, so in this case 'S' means SECOND.
                data.mainValue.unit.setMeasurement(Data.Value.Unit.Measurement.SECOND);
            }
            else
            {
                // ... otherwise, 'S' means SIEMENS.
                data.mainValue.unit.setMeasurement(Data.Value.Unit.Measurement.SIEMENS);
            }
        }

        if (checkMask(packet[14], BitMask.MAIN_DEG_C))
        {
            data.mainValue.unit.setMeasurement(Data.Value.Unit.Measurement.DEG_C);
        }

        // Main value unit type.
        data.mainValue.unit.setType(Data.Value.Unit.Type.NONE); // Clear.

        if (checkMask(packet[5], BitMask.MAIN_AC))
        {
            data.mainValue.unit.setType(Data.Value.Unit.Type.AC);
        }

        if (checkMask(packet[5], BitMask.MAIN_DC))
        {
            data.mainValue.unit.setType(Data.Value.Unit.Type.DC);
        }

        if (checkMask(packet[4], BitMask.MAIN_PW))
        {
            data.mainValue.unit.setType(Data.Value.Unit.Type.PW);
        }

        // Bar graph...
        Integer barGraph = null;

        // Check if Bar Graph 0 segment active (i.e. bar graph displayed).
        if (checkMask(packet[4], BitMask.BAR_GRAPH_0))
        {
            // Bar graph displayed, so check all segments...
            barGraph = 0;
            barGraph += ((packet[4] & BitMask.BAR_GRAPH_1) >> 6);
            barGraph += ((packet[4] & BitMask.BAR_GRAPH_2) >> 2);
            barGraph += (packet[4] & BitMask.BAR_GRAPH_4);
            barGraph += ((packet[4] & BitMask.BAR_GRAPH_8) << 2);
            barGraph += ((packet[4] & BitMask.BAR_GRAPH_16) << 4);
            barGraph += ((packet[16] & BitMask.BAR_GRAPH_32) << 1);
            barGraph += ((packet[16] & BitMask.BAR_GRAPH_64) << 1);
            barGraph += ((packet[16] & BitMask.BAR_GRAPH_128) << 1);
            barGraph += ((packet[16] & BitMask.BAR_GRAPH_256) << 1);
            barGraph += ((packet[15] & BitMask.BAR_GRAPH_512) << 6);
            barGraph += ((packet[15] & BitMask.BAR_GRAPH_1K) << 8);
            barGraph += ((packet[15] & BitMask.BAR_GRAPH_2K) << 10);
            barGraph += ((packet[15] & BitMask.BAR_GRAPH_4K) << 12);
            barGraph += ((packet[15] & BitMask.BAR_GRAPH_8K) << 9);
            barGraph += ((packet[15] & BitMask.BAR_GRAPH_16K) << 9);
        }

        data.barGraph = barGraph;

        // Set annunciators...
        data.annunciators.autoOff = checkMask(packet[9], BitMask.FLAG_AUTO_OFF);
        data.annunciators.pulse = checkMask(packet[9], BitMask.FLAG_PULSE);
        data.annunciators.maximum = checkMask(packet[10], BitMask.FLAG_MAXIMUM);
        data.annunciators.posPeak = checkMask(packet[10], BitMask.FLAG_POS_PEAK);
        data.annunciators.relative = checkMask(packet[10], BitMask.FLAG_RELATIVE);
        data.annunciators.recall = checkMask(packet[10], BitMask.FLAG_RECALL);
        data.annunciators.goNg = checkMask(packet[10], BitMask.FLAG_GO_NG);
        data.annunciators.posPercent = checkMask(packet[10], BitMask.FLAG_POS_PERCENT);
        data.annunciators.rs232c = checkMask(packet[9], BitMask.FLAG_RS232C);
        data.annunciators.positive = checkMask(packet[8], BitMask.FLAG_POSITIVE);
        data.annunciators.negative = checkMask(packet[8], BitMask.FLAG_NEGATIVE);
        data.annunciators.minimum = checkMask(packet[9], BitMask.FLAG_MINIMUM);
        data.annunciators.negPeak = checkMask(packet[9], BitMask.FLAG_NEG_PEAK);
        data.annunciators.average = checkMask(packet[9], BitMask.FLAG_AVERAGE);
        data.annunciators.store = checkMask(packet[9], BitMask.FLAG_STORE);
        data.annunciators.reference = checkMask(packet[10], BitMask.FLAG_REFERENCE);
        data.annunciators.negPercent = checkMask(packet[10], BitMask.FLAG_NEG_PERCENT);
        data.annunciators.lowBattery = checkMask(packet[5], BitMask.FLAG_LOW_BATTERY);

        // The following are refered to as sub (value) units in the manual, but they seem like global?
        data.annunciators.zenerDiode = checkMask(packet[3], BitMask.FLAG_ZENER_DIODE);
        data.annunciators.range = checkMask(packet[3], BitMask.FLAG_RANGE);
        data.annunciators.hold = checkMask(packet[3], BitMask.FLAG_HOLD);
        data.annunciators.duty = checkMask(packet[3], BitMask.FLAG_DUTY);
        data.annunciators.continuity = checkMask(packet[3], BitMask.FLAG_CONTINUITY);

        // Notify using the event listener if one is set.
        if (eventListener != null)
        {
            eventListener.dataUpdateEvent();
        }
    }


    //-----------------------------------------------------------------------
    /**
     * Sets an EventListener to be notified when data is received over the Serial Port.
     *
     * @param eventListener An EventListener Object to be notified when data is received
     */
    public void setEventListener(EventListener eventListener)
    {
        this.eventListener = eventListener;
    }

}
