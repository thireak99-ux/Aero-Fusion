package com.aircampus.ui;

import com.aircampus.exception.*;
import com.aircampus.model.*;
import com.aircampus.util.Checks;
import java.util.*;
import java.util.function.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

public final class Ui {
  private Ui() {}

  public static void error(Window owner, Throwable error) {
    String message =
        error instanceof AppException
            ? error.getMessage()
            : "The operation could not finish. Please try again or inspect the console.";
    if (!(error instanceof AppException)) error.printStackTrace();
    Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
    a.initOwner(owner);
    a.setHeaderText("Please check your input");
    a.showAndWait();
  }

  public static void info(Window owner, String title, String message) {
    Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
    a.initOwner(owner);
    a.setHeaderText(title);
    a.getDialogPane().setMinWidth(500);
    a.showAndWait();
  }

  public static boolean confirm(Window owner, String title, String message) {
    Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
    a.initOwner(owner);
    a.setHeaderText(title);
    return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
  }

  public static <T> T selected(TableView<T> table, String label) {
    return Checks.required(table.getSelectionModel().getSelectedItem(), label);
  }

  public static <T> TableColumn<T, String> column(
      TableView<T> table, String title, Function<T, Object> value) {
    TableColumn<T, String> c = new TableColumn<>(title);
    c.setCellValueFactory(
        d -> new ReadOnlyStringWrapper(String.valueOf(value.apply(d.getValue()))));
    c.setPrefWidth(135);
    table.getColumns().add(c);
    return c;
  }

  public static <T> void table(TableView<T> table) {
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setPlaceholder(new Label("No records to display."));
  }

  private static Object key(Object value) {
    if (value instanceof Flight x) return x.id();
    if (value instanceof Booking x) return x.reference();
    if (value instanceof GroundTask x) return x.id();
    if (value instanceof Aircraft x) return x.id();
    if (value instanceof CrewMember x) return x.id();
    if (value instanceof RunwaySlot x) return x.id();
    if (value instanceof Incident x) return x.id();
    if (value instanceof Claim x) return x.id();
    return value;
  }

  public static <T> void replace(TableView<T> table, List<T> list) {
    Object old = key(table.getSelectionModel().getSelectedItem());
    table.getItems().setAll(list);
    for (T item : list)
      if (Objects.equals(key(item), old)) {
        table.getSelectionModel().select(item);
        break;
      }
  }

  public static <T> void replace(ComboBox<T> box, List<T> list) {
    Object old = key(box.getValue());
    box.getItems().setAll(list);
    for (T item : list)
      if (Objects.equals(key(item), old)) {
        box.setValue(item);
        return;
      }
    if (!list.isEmpty()) box.setValue(list.get(0));
  }

  @SafeVarargs
  public static <T> ComboBox<T> choice(T... values) {
    ComboBox<T> c = new ComboBox<>();
    c.getItems().addAll(values);
    if (values.length > 0) c.setValue(values[0]);
    c.setMaxWidth(Double.MAX_VALUE);
    return c;
  }

  public static TextField text(String value) {
    return new TextField(value == null ? "" : value);
  }

  public static void textWindow(Stage owner, String title, String text) {
    Stage s = new Stage();
    s.initOwner(owner);
    TextArea area = new TextArea(text);
    area.setEditable(false);
    area.setWrapText(true);
    Button back = new Button("← Back to dashboard");
    back.setOnAction(
        e -> {
          s.close();
          owner.toFront();
          owner.requestFocus();
        });
    HBox navigation = new HBox(back);
    navigation.setPadding(new javafx.geometry.Insets(12));
    BorderPane content = new BorderPane(area);
    content.setTop(navigation);
    s.setScene(new Scene(content, 760, 560));
    s.setTitle(title);
    s.show();
  }

  public static final class Form {
    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final GridPane grid = new GridPane();
    private int row;

    public Form(Window owner, String title) {
      dialog.initOwner(owner);
      dialog.setTitle(title);
      dialog.setHeaderText(title);
      dialog.setResizable(true);
      dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
      grid.setHgap(16);
      grid.setVgap(10);
      grid.setStyle("-fx-padding:16;");
      ScrollPane scroll = new ScrollPane(grid);
      scroll.setFitToWidth(true);
      scroll.setPrefViewportHeight(480);
      scroll.setPrefViewportWidth(640);
      dialog.getDialogPane().setContent(scroll);
    }

    public <T extends Node> T add(String label, T control) {
      Label l = new Label(label);
      l.setWrapText(true);
      l.setMinWidth(185);
      l.setMaxWidth(200);
      grid.add(l, 0, row);
      grid.add(control, 1, row++);
      if (control instanceof Region r) {
        r.setMaxWidth(Double.MAX_VALUE);
        r.setPrefWidth(340);
      }
      GridPane.setHgrow(control, Priority.ALWAYS);
      return control;
    }

    public void note(String text) {
      Label l = new Label(text);
      l.setWrapText(true);
      l.setMaxWidth(540);
      grid.add(l, 0, row++, 2, 1);
    }

    public boolean submit(Runnable action) {
      boolean[] saved = {false};
      dialog
          .getDialogPane()
          .lookupButton(ButtonType.OK)
          .addEventFilter(
              javafx.event.ActionEvent.ACTION,
              e -> {
                try {
                  action.run();
                  saved[0] = true;
                } catch (RuntimeException ex) {
                  e.consume();
                  Ui.error(dialog.getOwner(), ex);
                }
              });
      dialog.showAndWait();
      return saved[0];
    }
  }
}
