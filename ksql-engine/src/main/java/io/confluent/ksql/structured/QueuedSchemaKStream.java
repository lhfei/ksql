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

package io.confluent.ksql.structured;

import io.confluent.ksql.GenericRow;
import io.confluent.ksql.parser.tree.Expression;
import io.confluent.ksql.planner.plan.OutputNode;
import io.confluent.ksql.processing.log.ProcessingLogContext;
import io.confluent.ksql.util.KsqlException;
import io.confluent.ksql.util.SelectExpression;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Windowed;

public class QueuedSchemaKStream<K> extends SchemaKStream<K> {

  private final BlockingQueue<KeyValue<String, GenericRow>> rowQueue =
      new LinkedBlockingQueue<>(100);

  @SuppressWarnings("unchecked") // needs investigating
  QueuedSchemaKStream(final SchemaKStream<K> schemaKStream, final QueryContext queryContext) {
    super(
        schemaKStream.schema,
        schemaKStream.getKstream(),
        schemaKStream.keyField,
        schemaKStream.sourceSchemaKStreams,
        schemaKStream.keySerde,
        Type.SINK,
        schemaKStream.ksqlConfig,
        schemaKStream.functionRegistry,
        queryContext
    );

    final OutputNode output = schemaKStream.outputNode();
    setOutputNode(output);
    kstream.foreach(new QueuedSchemaKStream.QueuePopulator(rowQueue, output.getCallback()));
  }

  public BlockingQueue<KeyValue<String, GenericRow>> getQueue() {
    return rowQueue;
  }

  @Override
  public SchemaKStream<K> into(
      final String kafkaTopicName,
      final Serde<GenericRow> topicValueSerDe,
      final Set<Integer> rowkeyIndexes
  ) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SchemaKStream<K> filter(
      final Expression filterExpression,
      final QueryContext.Stacker contextStacker,
      final ProcessingLogContext processingLogContext) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SchemaKStream<K> select(
      final List<SelectExpression> expressions,
      final QueryContext.Stacker contextStacker,
      final ProcessingLogContext processingLogContext) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SchemaKStream<K> leftJoin(
      final SchemaKTable<K> schemaKTable,
      final Schema joinSchema,
      final Field joinKey,
      final Serde<GenericRow> joinSerde,
      final QueryContext.Stacker contextStacker
  ) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SchemaKStream<K> selectKey(
      final Field newKeyField,
      final boolean updateRowKey,
      final QueryContext.Stacker contextStacker) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SchemaKGroupedStream groupBy(
      final Serde<GenericRow> valSerde,
      final List<Expression> groupByExpressions,
      final QueryContext.Stacker contextStacker) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Field getKeyField() {
    return super.getKeyField();
  }

  @Override
  public Schema getSchema() {
    return super.getSchema();
  }

  @Override
  public KStream<K, GenericRow> getKstream() {
    return super.getKstream();
  }

  @Override
  public List<SchemaKStream> getSourceSchemaKStreams() {
    return super.getSourceSchemaKStreams();
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private static final class QueuePopulator<K> implements ForeachAction<K, GenericRow> {
    private final BlockingQueue<KeyValue<String, GenericRow>> queue;
    private final OutputNode.Callback callback;

    QueuePopulator(
        final BlockingQueue<KeyValue<String, GenericRow>> queue,
        final OutputNode.Callback callback
    ) {
      this.queue = queue;
      this.callback = Objects.requireNonNull(callback, "callback");
    }

    @Override
    public void apply(final K key, final GenericRow row) {
      try {
        if (row == null) {
          return;
        }

        if (!callback.shouldQueue()) {
          return;
        }

        final String keyString = getStringKey(key);
        queue.put(new KeyValue<>(keyString, row));

        callback.onQueued();
      } catch (final InterruptedException exception) {
        throw new KsqlException("InterruptedException while enqueueing:" + key);
      }
    }

    private String getStringKey(final K key) {
      if (key instanceof Windowed) {
        final Windowed windowedKey = (Windowed) key;
        return String.format("%s : %s", windowedKey.key(), windowedKey.window());
      }

      return Objects.toString(key);
    }
  }
}
