package fr.romainmoreau.epaper.client.common;

import java.awt.Point;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import fr.romainmoreau.epaper.client.api.Color;
import fr.romainmoreau.epaper.client.api.DisplayDirection;
import fr.romainmoreau.epaper.client.api.DrawingColors;
import fr.romainmoreau.epaper.client.api.EPaperClient;
import fr.romainmoreau.epaper.client.api.EPaperException;
import fr.romainmoreau.epaper.client.api.EPaperResponseException;
import fr.romainmoreau.epaper.client.api.FontSize;
import fr.romainmoreau.epaper.client.api.HorizontalAlignment;
import fr.romainmoreau.epaper.client.api.VerticalAlignment;
import fr.romainmoreau.epaper.client.api.table.CellContent;
import fr.romainmoreau.epaper.client.api.table.CellContentDrawer;
import fr.romainmoreau.epaper.client.api.table.Table;
import fr.romainmoreau.epaper.client.common.command.ClearCommand;
import fr.romainmoreau.epaper.client.common.command.Command;
import fr.romainmoreau.epaper.client.common.command.DisplayTextCommand;
import fr.romainmoreau.epaper.client.common.command.DrawLineCommand;
import fr.romainmoreau.epaper.client.common.command.DrawPointCommand;
import fr.romainmoreau.epaper.client.common.command.DrawRectangleCommand;
import fr.romainmoreau.epaper.client.common.command.FillRectangleCommand;
import fr.romainmoreau.epaper.client.common.command.GetDisplayDirectionCommand;
import fr.romainmoreau.epaper.client.common.command.GetDrawingColorsCommand;
import fr.romainmoreau.epaper.client.common.command.GetFontSizeCommand;
import fr.romainmoreau.epaper.client.common.command.RefreshAndUpdateCommand;
import fr.romainmoreau.epaper.client.common.command.SetDisplayDirectionCommand;
import fr.romainmoreau.epaper.client.common.command.SetDrawingColorsCommand;
import fr.romainmoreau.epaper.client.common.command.SetFontSizeCommand;
import fr.romainmoreau.epaper.client.common.table.DrawableBorder;
import fr.romainmoreau.epaper.client.common.table.DrawableCell;
import fr.romainmoreau.epaper.client.common.table.DrawableTable;
import fr.romainmoreau.epaper.client.common.table.Tables;

public abstract class AbstractEPaperClient implements EPaperClient, CellContentDrawer {
	private static final byte[] RESPONSE_OK = "OK".getBytes(StandardCharsets.US_ASCII);

	private final long timeout;

	public AbstractEPaperClient(long timeout) {
		this.timeout = timeout;
	}

	@Override
	public synchronized void drawImage(int x, int y, InputStream inputStream) throws IOException, EPaperException {
		Coordinates.validateCoordinates(x, y);
		Map<Color, List<Point>> colorPointsMap = Colors.getColorPointsMap(inputStream);
		for (Color color : Color.values()) {
			setDrawingColors(new DrawingColors(color, Color.WHITE));
			for (Point point : colorPointsMap.get(color)) {
				drawPoint(x + point.x, y + point.y);
			}
		}
	}

	@Override
	public synchronized void drawPadded(int x0, int y0, int x1, int y1, int leftPadding, int rightPadding,
			int topPadding, int bottomPadding, CellContent cellContent) throws IOException, EPaperException {
		int topLeftX = Coordinates.getTopLeftX(x0, x1);
		int topLeftY = Coordinates.getTopLeftY(y0, y1);
		int bottomRightX = Coordinates.getBottomRightX(x0, x1);
		int bottomRightY = Coordinates.getBottomRightY(y0, y1);
		cellContent.draw(topLeftX + leftPadding, topLeftY + topPadding, bottomRightX - rightPadding,
				bottomRightY - bottomPadding, this);
	}

	@Override
	public synchronized void drawText(int x0, int y0, int x1, int y1, FontSize fontSize, int lineSpacing,
			HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment, String text)
			throws IOException, EPaperException {
		displayText(x0, y0, x1, y1, fontSize, lineSpacing, horizontalAlignment, verticalAlignment, text);
	}

	@Override
	public synchronized void drawTable(int x0, int y0, int x1, int y1, Table table)
			throws IOException, EPaperException {
		Coordinates.validateCoordinates(x0, y0);
		Coordinates.validateCoordinates(x1, y1);
		int topLeftX = Coordinates.getTopLeftX(x0, x1);
		int topLeftY = Coordinates.getTopLeftY(y0, y1);
		int width = Coordinates.getBottomRightX(x0, x1) - topLeftX;
		int height = Coordinates.getBottomRightY(y0, y1) - topLeftY;
		Tables.validateTable(width, height, table);
		DrawableTable drawableTable = Tables.getDrawableTable(width, height, topLeftX, topLeftY, table);
		for (DrawableCell drawableCell : drawableTable.getDrawableCells()) {
			if (drawableCell.getBackgroundColor() != null) {
				setDrawingColors(
						new DrawingColors(drawableCell.getBackgroundColor(), drawableCell.getBackgroundColor()));
				fillRectangle(drawableCell.getX0(), drawableCell.getY0(), drawableCell.getX1(), drawableCell.getY1());
			}
			for (CellContent cellContent : drawableCell.getCellContents()) {
				cellContent.draw(drawableCell.getX0(), drawableCell.getY0(), drawableCell.getX1(), drawableCell.getY1(),
						this);
			}
		}
		for (Entry<Color, List<DrawableBorder>> colorDrawableBorderListEntry : drawableTable.getDrawableBorders()
				.stream().collect(Collectors.groupingBy(DrawableBorder::getColor)).entrySet()) {
			setDrawingColors(
					new DrawingColors(colorDrawableBorderListEntry.getKey(), colorDrawableBorderListEntry.getKey()));
			for (DrawableBorder drawableBorder : colorDrawableBorderListEntry.getValue()) {
				fillRectangle(drawableBorder.getX0(), drawableBorder.getY0(), drawableBorder.getX1(),
						drawableBorder.getY1());
			}
		}
	}

	@Override
	public synchronized void refreshAndUpdate() throws IOException, EPaperException {
		sendCommand(new RefreshAndUpdateCommand());
		waitForResponse();
		checkResponseOK();
	}

	@Override
	public synchronized void clear() throws IOException, EPaperException {
		sendCommand(new ClearCommand());
		waitForResponse();
		checkResponseOK();
	}

	@Override
	public synchronized void drawPoint(int x, int y) throws IOException, EPaperException {
		Coordinates.validateCoordinates(x, y);
		sendCommand(new DrawPointCommand(x, y));
		waitForResponse();
		checkResponseOK();
	}

	@Override
	public synchronized void drawLine(int x0, int y0, int x1, int y1) throws IOException, EPaperException {
		Coordinates.validateCoordinates(x0, y0);
		Coordinates.validateCoordinates(x1, y1);
		sendCommand(new DrawLineCommand(x0, y0, x1, y1));
		waitForResponse();
		checkResponseOK();
	}

	@Override
	public synchronized void drawRectangle(int x0, int y0, int x1, int y1) throws IOException, EPaperException {
		Coordinates.validateCoordinates(x0, y0);
		Coordinates.validateCoordinates(x1, y1);
		sendCommand(new DrawRectangleCommand(x0, y0, x1, y1));
		waitForResponse();
		checkResponseOK();
	}

	@Override
	public synchronized void fillRectangle(int x0, int y0, int x1, int y1) throws IOException, EPaperException {
		Coordinates.validateCoordinates(x0, y0);
		Coordinates.validateCoordinates(x1, y1);
		sendCommand(new FillRectangleCommand(x0, y0, x1, y1));
		waitForResponse();
		checkResponseOK();
	}

	@Override
	public synchronized void displayText(int x, int y, String text) throws IOException, EPaperException {
		Coordinates.validateCoordinates(x, y);
		sendCommand(new DisplayTextCommand(x, y, text));
		waitForResponse();
		checkResponseOK();
	}

	@Override
	public synchronized void displayText(int x0, int y0, int x1, int y1, FontSize fontSize, int lineSpacing,
			HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment, String text)
			throws IOException, EPaperException {
		if (text == null || text.isEmpty()) {
			return;
		}
		Coordinates.validateCoordinates(x0, y0);
		Coordinates.validateCoordinates(x1, y1);
		Lines.validateFontSize(fontSize);
		Lines.validateSpacing(lineSpacing);
		Lines.validateHorizontalAlignment(horizontalAlignment);
		Lines.validateVerticalAlignment(verticalAlignment);
		Lines.validateText(text);
		int topLeftX = Coordinates.getTopLeftX(x0, x1);
		int topLeftY = Coordinates.getTopLeftY(y0, y1);
		int width = Coordinates.getBottomRightX(x0, x1) - topLeftX;
		int height = Coordinates.getBottomRightY(y0, y1) - topLeftY;
		List<Line> lines = Lines.getLines(width, height, topLeftX, topLeftY, fontSize, lineSpacing, horizontalAlignment,
				verticalAlignment, text);
		setFontSize(fontSize);
		for (Line line : lines) {
			displayText(line.getX(), line.getY(), line.getText());
		}
	}

	@Override
	public synchronized DrawingColors getDrawingColors() throws IOException, EPaperException {
		sendCommand(new GetDrawingColorsCommand());
		waitForResponse();
		checkResponsePresent();
		try {
			return Colors.getDrawingColors(getResponse());
		} catch (Exception e) {
			throw new EPaperResponseException(getResponse(), e);
		}

	}

	@Override
	public synchronized void setDrawingColors(DrawingColors drawingColors) throws IOException, EPaperException {
		Colors.validateDrawingColors(drawingColors);
		sendCommand(new SetDrawingColorsCommand(drawingColors));
		waitForResponse();
		checkResponseOK();
	}

	@Override
	public synchronized FontSize getFontSize() throws IOException, EPaperException {
		sendCommand(new GetFontSizeCommand());
		waitForResponse();
		checkResponsePresent();
		try {
			return Lines.getFontSize(getResponse());
		} catch (Exception e) {
			throw new EPaperResponseException(getResponse(), e);
		}

	}

	@Override
	public synchronized void setFontSize(FontSize fontSize) throws IOException, EPaperException {
		Lines.validateFontSize(fontSize);
		sendCommand(new SetFontSizeCommand(fontSize));
		waitForResponse();
		checkResponseOK();
	}

	@Override
	public synchronized void setDisplayDirection(DisplayDirection displayDirection)
			throws IOException, EPaperException {
		Coordinates.validateDisplayDirection(displayDirection);
		sendCommand(new SetDisplayDirectionCommand(displayDirection));
		waitForResponse();
		checkResponseOK();
	}

	@Override
	public synchronized DisplayDirection getDisplayDirection() throws IOException, EPaperException {
		sendCommand(new GetDisplayDirectionCommand());
		waitForResponse();
		checkResponsePresent();
		try {
			return Coordinates.getDisplayDirection(getResponse());
		} catch (Exception e) {
			throw new EPaperResponseException(getResponse(), e);
		}

	}

	public long getTimeout() {
		return timeout;
	}

	protected abstract byte[] getResponse();

	protected abstract void sendCommand(Command command) throws IOException;

	protected abstract void waitForResponse(long timeout);

	private void waitForResponse() {
		waitForResponse(timeout);
	}

	private void checkResponsePresent() throws EPaperResponseException {
		if (getResponse() == null) {
			throw new EPaperResponseException("No response");
		}
	}

	private void checkResponseOK() throws EPaperResponseException {
		checkResponsePresent();
		if (!Arrays.equals(RESPONSE_OK, getResponse())) {
			throw new EPaperResponseException(getResponse());
		}
	}
}
