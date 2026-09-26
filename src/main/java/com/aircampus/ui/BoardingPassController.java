package com.aircampus.ui;

import com.aircampus.model.*;
import com.aircampus.util.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.util.Map;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.json.JSONObject;

public final class BoardingPassController extends LiveController {
  @FXML private VBox passCard;
  @FXML
  private Label validityLabel,
      nameLabel,
      routeLabel,
      flightLabel,
      seatLabel,
      gateLabel,
      timeLabel,
      referenceLabel;
  @FXML private ImageView qrImage;
  private String reference;
  private boolean valid;

  @Override
  protected void setup() {}

  public void setReference(String reference) {
    this.reference = reference;
    update();
  }

  @Override
  protected void update() {
    if (reference == null) return;
    try {
      Booking b = c.bookings.boardingPass(s, reference);
      Flight f = c.flights.get(s, b.flightId());
      valid = true;
      validityLabel.setText("CHECKED IN • " + f.status());
      nameLabel.setText(b.passengerName());
      routeLabel.setText(f.origin().name() + "  →  " + f.destination().name());
      flightLabel.setText(f.number());
      seatLabel.setText(b.seat() + " / " + b.fareClass());
      gateLabel.setText(f.gate().isBlank() ? "TBA" : f.gate());
      timeLabel.setText(
          "Boarding: "
              + Formats.time(f.estimatedDeparture() - 2700)
              + "\nDeparture: "
              + Formats.time(f.estimatedDeparture()));
      referenceLabel.setText(b.reference());
      String payload =
          new JSONObject()
              .put("booking", b.reference())
              .put("name", b.passengerName())
              .put("flight", f.number())
              .put("seat", b.seat())
              .put("gate", f.gate())
              .toString();
      BitMatrix bits =
          new MultiFormatWriter()
              .encode(
                  payload,
                  BarcodeFormat.QR_CODE,
                  240,
                  240,
                  Map.of(EncodeHintType.CHARACTER_SET, "UTF-8"));
      WritableImage image = new WritableImage(240, 240);
      for (int y = 0; y < 240; y++)
        for (int x = 0; x < 240; x++)
          image.getPixelWriter().setArgb(x, y, bits.get(x, y) ? 0xff0f172a : 0xffffffff);
      qrImage.setImage(image);
    } catch (Exception e) {
      valid = false;
      validityLabel.setText("INVALID • " + e.getMessage());
      qrImage.setImage(null);
    }
  }

  @FXML
  public void onExport() {
    safe(
        () -> {
          update();
          Checks.that(valid, "This boarding pass is invalid.");
          FileChooser chooser = new FileChooser();
          chooser.setInitialFileName("boarding-pass-" + reference + ".png");
          chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
          var file = chooser.showSaveDialog(stage);
          if (file != null)
            try {
              javax.imageio.ImageIO.write(
                  SwingFXUtils.fromFXImage(passCard.snapshot(null, null), null), "png", file);
              Ui.info(
                  stage,
                  "Boarding pass exported",
                  "Saved "
                      + file.getName()
                      + ". Exported files are snapshots; security must revalidate the booking"
                      + " reference.");
            } catch (java.io.IOException e) {
              throw new com.aircampus.exception.AppException("Boarding pass export failed.", e);
            }
        });
  }

  @FXML
  public void onPrint() {
    safe(
        () -> {
          update();
          Checks.that(valid, "This boarding pass is invalid.");
          PrinterJob job = PrinterJob.createPrinterJob();
          if (job == null) {
            Ui.info(
                stage,
                "Printing simulation",
                "No printer is available. Export the pass as PNG for the demo.");
            return;
          }
          if (job.showPrintDialog(stage)) {
            double scale =
                Math.min(
                    1,
                    job.getJobSettings().getPageLayout().getPrintableWidth() / passCard.getWidth());
            javafx.scene.transform.Scale transform = new javafx.scene.transform.Scale(scale, scale);
            passCard.getTransforms().add(transform);
            try {
              if (job.printPage(passCard)) job.endJob();
              else
                throw new com.aircampus.exception.AppException(
                    "The printer could not print the boarding pass.");
            } finally {
              passCard.getTransforms().remove(transform);
            }
          }
        });
  }
}
