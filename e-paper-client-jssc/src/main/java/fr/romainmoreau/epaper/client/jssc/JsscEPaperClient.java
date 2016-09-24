package fr.romainmoreau.epaper.client.jssc;

import java.io.IOException;

import fr.romainmoreau.epaper.client.common.AbstractEPaperClient;
import fr.romainmoreau.epaper.client.common.command.Command;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortException;

public class JsscEPaperClient extends AbstractEPaperClient {
	private static final long TIMEOUT = 5000;

	private SerialPort serialPort;

	private volatile byte[] response;

	public JsscEPaperClient(String portName) throws IOException {
		try {
			serialPort = new SerialPort(portName);
			serialPort.openPort();
			serialPort.setParams(SerialPort.BAUDRATE_115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
					SerialPort.PARITY_NONE, false, false);
			serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
			serialPort.addEventListener(this::serialEvent, SerialPort.MASK_RXCHAR);
		} catch (SerialPortException e) {
			throw new IOException(e);
		}
	}

	@Override
	public synchronized void close() throws IOException {
		try {
			serialPort.closePort();
		} catch (SerialPortException e) {
			throw new IOException(e);
		}
	}

	@Override
	protected byte[] getResponse() {
		return response;
	}

	@Override
	protected void sendCommand(Command command) throws IOException {
		try {
			response = null;
			serialPort.writeBytes(command.getFrame());
		} catch (SerialPortException e) {
			throw new IOException(e);
		}
	}

	@Override
	protected void waitForResponse() {
		try {
			wait(TIMEOUT);
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	private void serialEvent(SerialPortEvent serialPortEvent) {
		try {
			response = serialPort.readBytes(serialPortEvent.getEventValue());
			synchronized (JsscEPaperClient.this) {
				JsscEPaperClient.this.notify();
			}
		} catch (SerialPortException e) {
			throw new RuntimeException(e);
		}
	}
}
