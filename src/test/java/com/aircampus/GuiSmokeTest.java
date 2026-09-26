package com.aircampus;

import static org.junit.jupiter.api.Assertions.*;

import com.aircampus.model.*;
import com.aircampus.ui.*;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Optional: mvn -Pgui-test test. Runs actual FXML/controller initialization without a display. */
@EnabledIfSystemProperty(named = "airport.guiTests", matches = "true")
class GuiSmokeTest {
  @TempDir Path temp;

  @Test
  @SuppressWarnings("unchecked")
  void navigationReturnsToSameDashboardAndReusesOneWorkspace() throws Exception {
    try (AppContext c =
        new AppContext(
            temp.resolve("navigation.db"),
            Clock.fixed(Instant.parse("2026-09-17T01:00:00Z"), ZoneOffset.UTC),
            true)) {
      Session passenger = c.auth.login(Role.PASSENGER.demoEmail(), "Airport123", Role.PASSENGER);
      Flight flight = c.flights.all(passenger).get(0);
      Booking booking =
          c.bookings.book(
              passenger,
              new com.aircampus.service.BookingRequest(
                  flight.id(),
                  "Demo Passenger",
                  "Demo Passenger",
                  "AB123456",
                  LocalDate.of(2000, 1, 1),
                  "",
                  "",
                  "",
                  "3A",
                  FareClass.ECONOMY,
                  "None",
                  "Standard"),
              com.aircampus.service.PaymentDetails.demo(c.clock));
      c.bookings.webCheckIn(passenger, booking.reference());

      CompletableFuture<Void> done = new CompletableFuture<>();
      Platform.runLater(
          () -> {
            java.util.List<Stage> windows = new java.util.ArrayList<>();
            Throwable failure = null;
            try {
              Stage dashboard = new Stage();
              windows.add(dashboard);
              ViewFactory.dashboard(c, passenger, dashboard);
              PassengerController passengerScreen = (PassengerController) dashboard.getUserData();
              var tabs =
                  (javafx.scene.control.TabPane) dashboard.getScene().getRoot().lookup(".tab-pane");
              tabs.getSelectionModel().select(1);
              var bookings =
                  (javafx.scene.control.TableView<Booking>)
                      dashboard.getScene().getRoot().lookup("#bookingsTable");
              bookings.getSelectionModel().select(0);

              passengerScreen.onBoard();
              Stage workspace =
                  (Stage)
                      javafx.stage.Window.getWindows().stream()
                          .filter(w -> w instanceof Stage st && st.getOwner() == dashboard)
                          .findFirst()
                          .orElseThrow();
              windows.add(workspace);
              assertTrue(workspace.getUserData() instanceof BoardController);
              assertFalse(
                  ((javafx.scene.control.Button)
                          workspace.getScene().getRoot().lookup("#navDemoButton"))
                      .isVisible());
              passengerScreen.onMap();
              assertTrue(workspace.getUserData() instanceof MapController);
              assertEquals(
                  1,
                  javafx.stage.Window.getWindows().stream()
                      .filter(w -> w instanceof Stage st && st.getOwner() == dashboard)
                      .count());
              ((javafx.scene.control.Button)
                      workspace.getScene().getRoot().lookup("#navBoardButton"))
                  .fire();
              assertTrue(workspace.getUserData() instanceof BoardController);

              ((javafx.scene.control.Button)
                      workspace.getScene().getRoot().lookup("#navDashboardButton"))
                  .fire();
              assertFalse(workspace.isShowing());
              assertTrue(dashboard.isShowing());
              assertEquals("My bookings", tabs.getSelectionModel().getSelectedItem().getText());
              c.auth.require(passenger);

              passengerScreen.onPass();
              Stage passWindow =
                  (Stage)
                      javafx.stage.Window.getWindows().stream()
                          .filter(
                              w ->
                                  w instanceof Stage st
                                      && st.getOwner() == dashboard
                                      && st.isShowing())
                          .findFirst()
                          .orElseThrow();
              windows.add(passWindow);
              assertTrue(passWindow.getUserData() instanceof BoardingPassController);
              ((javafx.scene.control.Button)
                      passWindow.getScene().getRoot().lookup("#navDashboardButton"))
                  .fire();
              assertEquals("My bookings", tabs.getSelectionModel().getSelectedItem().getText());

              Session manager = c.auth.login(Role.MANAGER.demoEmail(), "Airport123", Role.MANAGER);
              Stage managerDashboard = new Stage();
              windows.add(managerDashboard);
              ViewFactory.dashboard(c, manager, managerDashboard);
              ((BaseController) managerDashboard.getUserData()).onDemoControls();
              Stage managerWorkspace =
                  (Stage)
                      javafx.stage.Window.getWindows().stream()
                          .filter(w -> w instanceof Stage st && st.getOwner() == managerDashboard)
                          .findFirst()
                          .orElseThrow();
              windows.add(managerWorkspace);
              assertTrue(managerWorkspace.getUserData() instanceof DemoController);
              ((javafx.scene.control.Button)
                      managerWorkspace.getScene().getRoot().lookup("#navTrackingButton"))
                  .fire();
              assertTrue(managerWorkspace.getUserData() instanceof MapController);
              var demoButton =
                  (javafx.scene.control.Button)
                      managerWorkspace.getScene().getRoot().lookup("#navDemoButton");
              assertTrue(demoButton.isVisible());
              demoButton.fire();
              assertTrue(managerWorkspace.getUserData() instanceof DemoController);
              ((javafx.scene.control.Button)
                      managerWorkspace.getScene().getRoot().lookup("#navDashboardButton"))
                  .fire();
              assertTrue(managerDashboard.isShowing());
            } catch (Throwable e) {
              failure = e;
            } finally {
              for (int i = windows.size() - 1; i >= 0; i--) {
                Stage window = windows.get(i);
                if (window.getUserData() instanceof ScreenController controller)
                  controller.dispose();
                window.hide();
              }
            }
            if (failure == null) done.complete(null);
            else done.completeExceptionally(failure);
          });
      done.get(40, TimeUnit.SECONDS);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void demoControlsAndDateFiltersWorkAcrossAnExpiredSearch() throws Exception {
    try (AppContext c =
        new AppContext(
            temp.resolve("dates.db"),
            Clock.fixed(Instant.parse("2026-09-06T01:00:00Z"), ZoneOffset.UTC),
            true)) {
      Session passenger = c.auth.login(Role.PASSENGER.demoEmail(), "Airport123", Role.PASSENGER);
      Session manager = c.auth.login(Role.MANAGER.demoEmail(), "Airport123", Role.MANAGER);
      CompletableFuture<Void> done = new CompletableFuture<>();
      Platform.runLater(
          () -> {
            java.util.List<Stage> windows = new java.util.ArrayList<>();
            Throwable failure = null;
            try {
              Stage passengerStage = new Stage();
              windows.add(passengerStage);
              ViewFactory.dashboard(c, passenger, passengerStage);
              PassengerController passengerScreen =
                  (PassengerController) passengerStage.getUserData();
              passengerScreen.onSearch();
              var flights =
                  (javafx.scene.control.TableView<Flight>)
                      passengerStage.getScene().getRoot().lookup("#flightsTable");
              assertEquals(1, flights.getItems().size());
              c.demo.setTime(manager, Instant.parse("2026-09-17T01:00:00Z"));
              c.demo.newWeek(manager);
              passengerScreen.onRefresh();
              assertEquals(
                  28, flights.getItems().size(), "Expired search must stop hiding new flights");
              var date =
                  (javafx.scene.control.DatePicker)
                      passengerStage.getScene().getRoot().lookup("#datePicker");
              date.setValue(LocalDate.of(2026, 9, 23));
              passengerScreen.onSearch();
              assertEquals(1, flights.getItems().size(), "A later travel date remains searchable");

              Stage boardStage = new Stage();
              windows.add(boardStage);
              BoardController board =
                  (BoardController)
                      ViewFactory.show(c, passenger, boardStage, "board", "Flight board");
              Parent root = boardStage.getScene().getRoot();
              var table = (javafx.scene.control.TableView<Flight>) root.lookup("#boardTable");
              var range = (javafx.scene.control.ComboBox<String>) root.lookup("#rangeBox");
              assertEquals(28, table.getItems().size());
              range.setValue("All dates / history");
              board.onRefresh();
              assertEquals(56, table.getItems().size());
              range.setValue("Selected date");
              ((javafx.scene.control.DatePicker) root.lookup("#boardDate"))
                  .setValue(LocalDate.of(2026, 9, 6));
              board.onRefresh();
              assertEquals(4, table.getItems().size(), "Saved historical flights remain visible");

              Stage demoStage = new Stage();
              windows.add(demoStage);
              DemoController demo =
                  (DemoController) ViewFactory.show(c, manager, demoStage, "demo", "Demo controls");
              demo.onPreset();
              var clockLabel =
                  (javafx.scene.control.Label) demoStage.getScene().getRoot().lookup("#clockLabel");
              assertTrue(c.clock.isFrozen());
              assertTrue(clockLabel.getText().contains("DEMO TIME (paused)"));
              assertTrue(
                  ((javafx.scene.control.TextArea)
                          demoStage.getScene().getRoot().lookup("#checklistArea"))
                      .getText()
                      .contains("Assigned crew: 3"));
              demo.onRealTime();
              assertFalse(c.clock.isFrozen());
              assertTrue(clockLabel.getText().contains("Current time:"));
            } catch (Throwable e) {
              failure = e;
            } finally {
              for (Stage window : windows) {
                if (window.getUserData() instanceof ScreenController controller)
                  controller.dispose();
                window.hide();
              }
            }
            if (failure == null) done.complete(null);
            else done.completeExceptionally(failure);
          });
      done.get(40, TimeUnit.SECONDS);
    }
  }

  @BeforeAll
  static void startFx() throws Exception {
    CountDownLatch ready = new CountDownLatch(1);
    Platform.startup(
        () -> {
          Platform.setImplicitExit(false);
          ready.countDown();
        });
    assertTrue(ready.await(20, TimeUnit.SECONDS));
  }

  @Test
  void loadAllScreensAndRenderEveryDashboard() throws Exception {
    try (AppContext c =
        new AppContext(
            temp.resolve("gui.db"),
            Clock.fixed(Instant.parse("2026-09-17T01:00:00Z"), ZoneOffset.UTC),
            true)) {
      CompletableFuture<Void> done = new CompletableFuture<>();
      Platform.runLater(
          () -> {
            try {
              Path output = Path.of("target/gui-screenshots");
              Files.createDirectories(output);
              for (String file : new String[] {"login", "register"}) {
                Stage stage = new Stage();
                ScreenController controller = ViewFactory.show(c, null, stage, file, file);
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                javax.imageio.ImageIO.write(
                    SwingFXUtils.fromFXImage(stage.getScene().snapshot(null), null),
                    "png",
                    output.resolve(file + ".png").toFile());
                controller.dispose();
                stage.hide();
              }
              for (Role role : Role.values()) {
                Session s = c.auth.login(role.demoEmail(), "Airport123", role);
                Stage stage = new Stage();
                ViewFactory.dashboard(c, s, stage);
                Parent root = stage.getScene().getRoot();
                root.applyCss();
                root.layout();
                assertNotNull(root.lookup("#statusLabel"), role.toString());
                assertTrue(root.getBoundsInLocal().getWidth() > 1000);
                javax.imageio.ImageIO.write(
                    SwingFXUtils.fromFXImage(stage.getScene().snapshot(null), null),
                    "png",
                    output.resolve(role.view() + ".png").toFile());
                ((ScreenController) stage.getUserData()).dispose();
                stage.hide();
              }
              Session passenger =
                  c.auth.login(Role.PASSENGER.demoEmail(), "Airport123", Role.PASSENGER);
              for (String file : new String[] {"board", "map", "boarding-pass"}) {
                Stage stage = new Stage();
                ScreenController controller = ViewFactory.show(c, passenger, stage, file, file);
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                assertNotNull(stage.getScene().getRoot());
                controller.dispose();
                stage.hide();
              }
              Session manager = c.auth.login(Role.MANAGER.demoEmail(), "Airport123", Role.MANAGER);
              Stage demoStage = new Stage();
              ScreenController demo =
                  ViewFactory.show(c, manager, demoStage, "demo", "Demo controls");
              demoStage.getScene().getRoot().applyCss();
              demoStage.getScene().getRoot().layout();
              assertNotNull(demoStage.getScene().getRoot().lookup("#clockLabel"));
              var demoImage = SwingFXUtils.fromFXImage(demoStage.getScene().snapshot(null), null);
              var png = new java.io.ByteArrayOutputStream();
              assertTrue(javax.imageio.ImageIO.write(demoImage, "png", png));
              byte[] screenshot = png.toByteArray();
              assertArrayEquals(
                  new byte[] {73, 69, 78, 68, (byte) 0xae, 66, 96, (byte) 0x82},
                  java.util.Arrays.copyOfRange(
                      screenshot, screenshot.length - 8, screenshot.length));
              Files.write(output.resolve("demo-controls.png"), screenshot);
              demo.dispose();
              demoStage.hide();
              done.complete(null);
            } catch (Throwable e) {
              done.completeExceptionally(e);
            }
          });
      done.get(40, TimeUnit.SECONDS);
    }
  }

  @Test
  void boardingPassQrMatchesBookingAndCancellationRemovesIt() throws Exception {
    try (AppContext c =
        new AppContext(
            temp.resolve("pass.db"),
            Clock.fixed(Instant.parse("2026-09-17T01:00:00Z"), ZoneOffset.UTC),
            true)) {
      Session passenger = c.auth.login(Role.PASSENGER.demoEmail(), "Airport123", Role.PASSENGER);
      Session ops = c.auth.login(Role.OPS.demoEmail(), "Airport123", Role.OPS);
      Flight flight = c.flights.all(passenger).get(0);
      var request =
          new com.aircampus.service.BookingRequest(
              flight.id(),
              "Demo Passenger",
              "Demo Passenger",
              "AB123456",
              LocalDate.of(2000, 1, 1),
              "",
              "",
              "",
              "3A",
              FareClass.ECONOMY,
              "None",
              "Standard");
      Booking booking =
          c.bookings.book(
              passenger,
              request,
              new com.aircampus.service.PaymentDetails("4111111111111111", "12/39", "123", true));
      c.bookings.webCheckIn(passenger, booking.reference());
      CompletableFuture<Void> done = new CompletableFuture<>();
      Platform.runLater(
          () -> {
            Stage stage = new Stage();
            try {
              BoardingPassController controller =
                  (BoardingPassController)
                      ViewFactory.show(c, passenger, stage, "boarding-pass", "Boarding pass");
              controller.setReference(booking.reference());
              Parent root = stage.getScene().getRoot();
              root.applyCss();
              root.layout();
              javafx.scene.image.Image qr =
                  ((javafx.scene.image.ImageView) root.lookup("#qrImage")).getImage();
              assertNotNull(qr);
              int width = (int) qr.getWidth(), height = (int) qr.getHeight();
              int[] pixels = new int[width * height];
              qr.getPixelReader()
                  .getPixels(
                      0,
                      0,
                      width,
                      height,
                      javafx.scene.image.PixelFormat.getIntArgbInstance(),
                      pixels,
                      0,
                      width);
              var source = new com.google.zxing.RGBLuminanceSource(width, height, pixels);
              String decoded =
                  new com.google.zxing.MultiFormatReader()
                      .decode(
                          new com.google.zxing.BinaryBitmap(
                              new com.google.zxing.common.HybridBinarizer(source)))
                      .getText();
              assertEquals(
                  booking.reference(), new org.json.JSONObject(decoded).getString("booking"));
              assertEquals("3A", new org.json.JSONObject(decoded).getString("seat"));
              Path output = Path.of("target/gui-screenshots");
              Files.createDirectories(output);
              javax.imageio.ImageIO.write(
                  SwingFXUtils.fromFXImage(stage.getScene().snapshot(null), null),
                  "png",
                  output.resolve("boarding-pass.png").toFile());
              c.flights.updateStatus(
                  ops, flight.id(), FlightStatus.CANCELLED, 0, "Test cancellation");
              controller.setReference(booking.reference());
              assertNull(((javafx.scene.image.ImageView) root.lookup("#qrImage")).getImage());
              assertTrue(
                  ((javafx.scene.control.Label) root.lookup("#validityLabel"))
                      .getText()
                      .startsWith("INVALID"));
              done.complete(null);
            } catch (Throwable e) {
              done.completeExceptionally(e);
            } finally {
              if (stage.getUserData() instanceof ScreenController sc) sc.dispose();
              stage.hide();
            }
          });
      done.get(20, TimeUnit.SECONDS);
    }
  }
}
