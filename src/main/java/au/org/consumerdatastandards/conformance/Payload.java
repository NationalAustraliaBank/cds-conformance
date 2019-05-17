package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.codegen.model.EndpointModel;

public class Payload {

    private PayloadType payloadType;

    private Class<?> dataClass;

    private EndpointModel endpointModel;

    public void setPayloadType(PayloadType payloadType) {
        this.payloadType = payloadType;
    }

    public Class<?> getDataClass() {
        return dataClass;
    }

    public void setDataClass(Class<?> dataClass) {
        this.dataClass = dataClass;
    }

    public void setEndpointModel(EndpointModel endpointModel) {
        this.endpointModel = endpointModel;
    }

    public String getDescription() {
        return payloadType.toString() +
            " in endpoint (" + endpointModel.getEndpoint().operationId()  + ") "
            + endpointModel.getEndpoint().path();
    }
}
