package fr.romainmoreau.epaper.web.api;

import java.io.IOException;

import javax.xml.bind.annotation.XmlAttribute;

import fr.romainmoreau.epaper.client.common.Color;
import fr.romainmoreau.epaper.client.common.DrawingColors;
import fr.romainmoreau.epaper.client.common.EPaperClient;
import fr.romainmoreau.epaper.client.common.EPaperException;

public class SetDrawingColors implements Command {
	private Color foreground;

	private Color background;

	@Override
	public void execute(EPaperClient ePaperClient) throws IOException, EPaperException {
		ePaperClient.setDrawingColors(new DrawingColors(foreground, background));
	}

	@XmlAttribute(required = true)
	public Color getForeground() {
		return foreground;
	}

	public void setForeground(Color foreground) {
		this.foreground = foreground;
	}

	@XmlAttribute(required = true)
	public Color getBackground() {
		return background;
	}

	public void setBackground(Color background) {
		this.background = background;
	}
}
