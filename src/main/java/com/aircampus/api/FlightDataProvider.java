package com.aircampus.api;

import com.aircampus.model.Position;
import java.util.List;

public interface FlightDataProvider {
  List<Position> fetch() throws Exception;
}
