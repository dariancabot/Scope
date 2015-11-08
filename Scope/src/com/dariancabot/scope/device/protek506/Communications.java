package com.dariancabot.scope.device.protek506;

import com.dariancabot.scope.exceptions.ProtocolException;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;


/**
 * Communications Object.
 *
 * @author Darian Cabot
 */
public final class Communications implements SerialPortEventListener
{
    private SerialPort serialPort;
    private final Decoder decoder;

    private static final byte packetEndByte = 0x0d; // ASCII code 13 (CR).

    // RS-232 Control lines.
    // TODO: Irrelevant? Remove?
    private boolean isCtsOn = false; // CTS = Clear To Send.
    private boolean isDsrOn = false; // DSR = Data Set Ready.

    private final byte[] packetBuffer = new byte[15]; // Packet should be between 6 and 15 bytes.
    private int packetBufferPosition = 0;

    /**
     * Used by {@link #bytesToHex(byte[])}
     */
    final protected static char[] hexArray = "0123456789abcdef".toCharArray();


    //-----------------------------------------------------------------------
    /**
     * Creates a new Communications instance.
     *
     * @param serialPort the SerialPort to be used
     * @param decoder    the Decoder to be used
     */
    public Communications(SerialPort serialPort, Decoder decoder)
    {
        this.serialPort = serialPort;
        this.decoder = decoder;
    }


    //-----------------------------------------------------------------------
    /**
     * Gets the SerialPort used for communications.
     *
     * @return the SerialPort used for communications
     */
    protected SerialPort getSerialPort()
    {
        return serialPort;
    }


    //-----------------------------------------------------------------------
    /**
     * Sets the SerialPort to be used for communications.
     *
     * @param serialPort the new SerialPort
     */
    protected void setSerialPort(SerialPort serialPort)
    {
        this.serialPort = serialPort;
    }


    //-----------------------------------------------------------------------
    /**
     * Implementation of the serialEvent method to see events that happened to the port. This only report those events that are set in the SerialPort mask.
     *
     * @param event the new SerialPort
     */
    @Override
    public void serialEvent(SerialPortEvent event)
    {

        switch (event.getEventType())
        {
            case SerialPortEvent.RXCHAR: // Data has been received.

                try
                {
                    byte[] rxBuffer = serialPort.readBytes();

                    for (int byteCount = 0; byteCount < rxBuffer.length; byteCount ++)
                    {
                        packetBuffer[packetBufferPosition] = rxBuffer[byteCount];

                        // Buffer overflow protection.
                        if (packetBufferPosition >= 14)
                        {
                            // Reset for next packet
                            packetBufferPosition = 0;
                        }
                        else
                        {
                            packetBufferPosition ++;

                            if (packetBuffer[packetBufferPosition] == packetEndByte)
                            {
                                // We have a full valid packet, decode it.
                                decoder.decodeSerialData(packetBuffer);

                                // Print valid packet in hex (debugging).
                                //System.out.println(bytesToHex(packetBuffer));
                                packetBufferPosition = 0;
                            }
                        }
                    }
                }
                catch (SerialPortException | ProtocolException e)
                {
                    ProtocolException pex = new ProtocolException("Error receiving serial data", e);
                    throw pex;
                }

                break;

            case SerialPortEvent.CTS:

                isCtsOn = (event.getEventValue() == 1); // If signal line is ON

                break;

            case SerialPortEvent.DSR:

                isDsrOn = (event.getEventValue() == 1); // If signal line is ON

                break;

            default:
                break;
        }
    }


    //-----------------------------------------------------------------------
    /**
     * Gets the status of the RS-232 CTS (Clear To Send) control line.
     *
     * @return the status of the CTS control line
     */
    public boolean isCtsOn()
    {
        return isCtsOn;
    }


    //-----------------------------------------------------------------------
    /**
     * Gets the status of the RS-232 DSR (Data Set Ready) control line.
     *
     * @return the status of the DSR control line
     */
    public boolean isDsrOn()
    {
        return isDsrOn;
    }


    //-----------------------------------------------------------------------
    /**
     * Converts a byte array into a hex String.
     *
     * @param bytes A byte array to be converted to a HEX String
     *
     * @return A String of HEX represntation of the passed byte array
     *
     * @see http://stackoverflow.com/a/9855338
     */
    public static String bytesToHex(byte[] bytes)
    {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j ++)
        {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }

}
