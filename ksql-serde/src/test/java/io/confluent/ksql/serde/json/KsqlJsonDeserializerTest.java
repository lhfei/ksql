/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License; you may not use this file
 * except in compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.serde.json;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.common.logging.StructuredLogger;
import io.confluent.ksql.GenericRow;
import io.confluent.ksql.processing.log.ProcessingLogContext;
import io.confluent.ksql.serde.SerdeTestUtils;
import io.confluent.ksql.serde.util.SerdeProcessingLogMessageFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class KsqlJsonDeserializerTest {

  private Schema orderSchema;
  private KsqlJsonDeserializer ksqlJsonDeserializer;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ProcessingLogContext processingLogContext = ProcessingLogContext.create();

  @Mock
  StructuredLogger recordLogger;

  @Rule
  public final MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void before() {
    orderSchema = SchemaBuilder.struct()
        .field("ordertime".toUpperCase(), org.apache.kafka.connect.data.Schema.OPTIONAL_INT64_SCHEMA)
        .field("orderid".toUpperCase(), org.apache.kafka.connect.data.Schema.OPTIONAL_INT64_SCHEMA)
        .field("itemid".toUpperCase(), org.apache.kafka.connect.data.Schema.OPTIONAL_STRING_SCHEMA)
        .field("orderunits".toUpperCase(), org.apache.kafka.connect.data.Schema.OPTIONAL_FLOAT64_SCHEMA)
        .field("arraycol".toUpperCase(), SchemaBuilder.array(org.apache.kafka.connect.data.Schema.OPTIONAL_FLOAT64_SCHEMA).optional().build())
        .field("mapcol".toUpperCase(), SchemaBuilder.map(org.apache.kafka.connect.data.Schema.OPTIONAL_STRING_SCHEMA, org.apache.kafka.connect.data.Schema.OPTIONAL_FLOAT64_SCHEMA).optional().build())
        .build();
    ksqlJsonDeserializer = new KsqlJsonDeserializer(
        orderSchema,
        false,
        recordLogger,
        processingLogContext);
  }

  @Test
  public void shouldDeserializeJsonCorrectly() throws JsonProcessingException {
    final Map<String, Object> orderRow = new HashMap<>();
    orderRow.put("ordertime", 1511897796092L);
    orderRow.put("@orderid", 1L);
    orderRow.put("itemid", "Item_1");
    orderRow.put("orderunits", 10.0);
    orderRow.put("arraycol", new Double[]{10.0, 20.0});
    orderRow.put("mapcol", Collections.singletonMap("key1", 10.0));

    final byte[] jsonBytes = objectMapper.writeValueAsBytes(orderRow);

    final GenericRow genericRow = ksqlJsonDeserializer.deserialize("", jsonBytes);
    assertThat(genericRow.getColumns().size(), equalTo(6));
    assertThat(genericRow.getColumns().get(0), equalTo(1511897796092L));
    assertThat(genericRow.getColumns().get(1), equalTo(1L));
    assertThat(genericRow.getColumns().get(2), equalTo("Item_1"));
    assertThat(genericRow.getColumns().get(3), equalTo(10.0));
  }

  @Test
  public void shouldDeserializeJsonCorrectlyWithRedundantFields() throws JsonProcessingException {
    final Map<String, Object> orderRow = new HashMap<>();
    orderRow.put("ordertime", 1511897796092L);
    orderRow.put("@orderid", 1L);
    orderRow.put("itemid", "Item_1");
    orderRow.put("orderunits", 10.0);
    orderRow.put("arraycol", new Double[]{10.0, 20.0});
    orderRow.put("mapcol", Collections.singletonMap("key1", 10.0));

    final byte[] jsonBytes = objectMapper.writeValueAsBytes(orderRow);

    final Schema newOrderSchema = SchemaBuilder.struct()
        .field("ordertime".toUpperCase(), org.apache.kafka.connect.data.Schema.OPTIONAL_INT64_SCHEMA)
        .field("orderid".toUpperCase(), org.apache.kafka.connect.data.Schema.OPTIONAL_INT64_SCHEMA)
        .field("itemid".toUpperCase(), org.apache.kafka.connect.data.Schema.OPTIONAL_STRING_SCHEMA)
        .field("orderunits".toUpperCase(), org.apache.kafka.connect.data.Schema.OPTIONAL_FLOAT64_SCHEMA)
        .build();
    final KsqlJsonDeserializer ksqlJsonDeserializer = new KsqlJsonDeserializer(
        newOrderSchema,
        false,
        recordLogger,
        processingLogContext);

    final GenericRow genericRow = ksqlJsonDeserializer.deserialize("", jsonBytes);
    assertThat(genericRow.getColumns().size(), equalTo(4));
    assertThat(genericRow.getColumns().get(0), equalTo(1511897796092L));
    assertThat(genericRow.getColumns().get(1), equalTo(1L));
    assertThat(genericRow.getColumns().get(2), equalTo("Item_1"));
    assertThat(genericRow.getColumns().get(3), equalTo(10.0));

  }

  @Test
  public void shouldDeserializeEvenWithMissingFields() throws JsonProcessingException {
    final Map<String, Object> orderRow = new HashMap<>();
    orderRow.put("ordertime", 1511897796092L);
    orderRow.put("@orderid", 1L);
    orderRow.put("itemid", "Item_1");
    orderRow.put("orderunits", 10.0);

    final byte[] jsonBytes = objectMapper.writeValueAsBytes(orderRow);

    final GenericRow genericRow = ksqlJsonDeserializer.deserialize("", jsonBytes);
    assertThat(genericRow.getColumns().size(), equalTo(6));
    assertThat(genericRow.getColumns().get(0), equalTo(1511897796092L));
    assertThat(genericRow.getColumns().get(1), equalTo(1L));
    assertThat(genericRow.getColumns().get(2), equalTo("Item_1"));
    assertThat(genericRow.getColumns().get(3), equalTo(10.0));
    assertThat(genericRow.getColumns().get(4), is(nullValue()));
    assertThat(genericRow.getColumns().get(5), is(nullValue()));
  }

  @Test
  public void shouldTreatNullAsNull() throws JsonProcessingException {
    final Map<String, Object> row = new HashMap<>();
    row.put("ordertime", null);
    row.put("@orderid", null);
    row.put("itemid", null);
    row.put("orderunits", null);
    row.put("arrayCol", new Double[]{0.0, null});
    row.put("mapCol", null);

    final GenericRow expected = new GenericRow(Arrays.asList(null, null, null, null, new Double[]{0.0, null}, null));
    final GenericRow genericRow = ksqlJsonDeserializer.deserialize(
        "", objectMapper.writeValueAsBytes(row));
    assertThat(genericRow, equalTo(expected));

  }

  @Test
  public void shouldCreateJsonStringForStructIfDefinedAsVarchar() throws JsonProcessingException {
    final Schema schema = SchemaBuilder.struct()
        .field("itemid".toUpperCase(), Schema.OPTIONAL_STRING_SCHEMA)
        .build();
    final KsqlJsonDeserializer deserializer = new KsqlJsonDeserializer(
        schema,
        false,
        recordLogger,
        processingLogContext);

    final GenericRow expected = new GenericRow(Collections.singletonList(
        "{\"CATEGORY\":{\"ID\":2,\"NAME\":\"Food\"},\"ITEMID\":6,\"NAME\":\"Item_6\"}"));
    final GenericRow genericRow = deserializer.deserialize("", "{\"itemid\":{\"CATEGORY\":{\"ID\":2,\"NAME\":\"Food\"},\"ITEMID\":6,\"NAME\":\"Item_6\"}}".getBytes(StandardCharsets.UTF_8));
    assertThat(genericRow, equalTo(expected));
  }

  @Test
  public void shouldLogDeserializationErrors() {
    // When:
    Throwable cause = null;
    final byte[] data = "{foo".getBytes(StandardCharsets.UTF_8);
    try {
      ksqlJsonDeserializer.deserialize("", data);
      fail("deserialize should have thrown");
    } catch (final SerializationException e) {
      cause = e.getCause();
    }

    // Then:
    SerdeTestUtils.shouldLogError(
        recordLogger,
        SerdeProcessingLogMessageFactory.deserializationErrorMsg(
            cause,
            Optional.ofNullable(data),
            processingLogContext.getConfig()).get());
  }
}