package cn.myfrank.stationbuilder.mtr;

import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.serializer.WriterBase;

/** Empty request body for MTR's {@code list_data} operation (MTR 4.0.x). */
public final class MtrEmptyRequest implements SerializedDataBase {

    public static final MtrEmptyRequest INSTANCE = new MtrEmptyRequest();

    private MtrEmptyRequest() {
    }

    @Override
    public void updateData(ReaderBase reader) {
    }

    @Override
    public void serializeData(WriterBase writer) {
    }
}
