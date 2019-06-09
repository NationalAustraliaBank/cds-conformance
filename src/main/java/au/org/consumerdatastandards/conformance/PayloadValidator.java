package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.api.common.models.BaseResponse;
import au.org.consumerdatastandards.api.common.models.LinksPaginated;
import au.org.consumerdatastandards.api.common.models.MetaPaginated;
import au.org.consumerdatastandards.api.common.models.PaginatedResponse;
import au.org.consumerdatastandards.codegen.ModelBuilder;
import au.org.consumerdatastandards.codegen.generator.Options;
import au.org.consumerdatastandards.conformance.util.ConformanceUtil;
import au.org.consumerdatastandards.conformance.util.ModelConformanceConverter;
import au.org.consumerdatastandards.support.EndpointResponse;
import au.org.consumerdatastandards.support.ResponseCode;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PayloadValidator {

    private static Logger LOGGER = LoggerFactory.getLogger(PayloadValidator.class);

    private ConformanceModel conformanceModel;

    public PayloadValidator() {
        ModelBuilder modelBuilder = new ModelBuilder(new Options());
        conformanceModel = ModelConformanceConverter.convert(modelBuilder.build());
    }

    public List<ConformanceError> validateFile(File jsonFile) {
        LOGGER.info("Validating " + jsonFile.getAbsolutePath());
        byte[] jsonData;
        try {
            jsonData = Files.readAllBytes(Paths.get(jsonFile.getCanonicalPath()));
            return validatePayload(jsonData);
        } catch (IOException e) {
            return Collections.singletonList(new ConformanceError().errorMessage(
                "Failed to load file " + jsonFile.getAbsolutePath()
            ));
        }
    }

    public List<ConformanceError> validatePayload(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.singletonList(
                new ConformanceError().errorMessage("Blank json text... Ignored."));
        }
        return validatePayload(json.getBytes());
    }

    private List<ConformanceError> validatePayload(byte[] jsonData) {
        for (Class<?> modelClass : conformanceModel.getPayloadModels()) {
            try {
                ObjectMapper objectMapper = createObjectMapper();
                Payload payload = conformanceModel.getPlayload(modelClass);
                Object data = objectMapper.readValue(jsonData, payload.getDataClass());
                List<ConformanceError> errors = new ArrayList<>();
                ConformanceUtil.checkAgainstModel(data, modelClass, errors);
                LOGGER.info("Found matching model " + modelClass.getSimpleName());
                return errors;
            } catch (IOException e) {
                // ignored
            }
        }
        return Collections.singletonList(new ConformanceError()
            .errorType(ConformanceError.Type.NO_MATCHING_MODEL)
            .errorMessage("No matching model found"));
    }

    public List<ConformanceError> validateResponse(String requestUrl, Object response, String operationId, ResponseCode responseCode) {
        List<ConformanceError> errors = new ArrayList<>();
        EndpointResponse endpointResponse = conformanceModel.getResponse(operationId, responseCode);
        if (endpointResponse == null) {
            return Collections.singletonList(new ConformanceError().errorMessage(
                String.format("No response model found for operation %s with response code %s", operationId, responseCode)));
        }
        Class<?> responseModel = endpointResponse.content();
        ConformanceUtil.checkAgainstModel(response, responseModel, errors);
        errors.addAll(checkMetaAndLinks(requestUrl, response));
        return errors;
    }

    public List<ConformanceError> validateResponse(String requestUrl, Object response, String operationId, int httpResponseCode) {
        ResponseCode responseCode = ResponseCode.fromCode(httpResponseCode);
        if (responseCode == null) {
            return Collections.singletonList(new ConformanceError().errorMessage(
                String.format("No response defined with code %d", httpResponseCode)
            ));
        }
        return validateResponse(requestUrl, response, operationId, responseCode);
    }

    public ObjectMapper createObjectMapper() {
        return new ObjectMapper().registerModule(new ParameterNamesModule())
            .registerModule(new Jdk8Module()).registerModule(new JavaTimeModule())
            .registerModule(new SimpleModule().setDeserializerModifier(new CglibBeanDeserializerModifier()))
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    private List<ConformanceError> checkMetaAndLinks(String requestUrl, Object response) {
        Integer page = 1, pageSize = 25;
        String pageParam = getParameter(requestUrl, "page");
        if (!StringUtils.isBlank(pageParam)) page = Integer.parseInt(pageParam);
        String pageSizeParam = getParameter(requestUrl, "page-size");
        if (!StringUtils.isBlank(pageSizeParam)) pageSize = Integer.parseInt(pageSizeParam);

        List<ConformanceError> errors = new ArrayList<>();
        if (response instanceof BaseResponse) {
            checkSelfLink(requestUrl, response, errors);
        } else if (response instanceof PaginatedResponse) {
            MetaPaginated meta = getMetaPaginated(response);
            LinksPaginated links = getLinksPaginated(response);
            if (meta == null) {
                errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                    .modelClass(response.getClass())
                    .dataObject(response)
                    .errorMessage(String.format("%s does not have meta data", response))
                );
            }
            if (links == null) {
                errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                    .modelClass(response.getClass())
                    .dataObject(response)
                    .errorMessage(String.format("%s does not have links data", response))
                );
            } else {
                String first = getFirst(links);
                String prev = getPrev(links);
                String next = getNext(links);
                String last = getLast(links);
                String self = getSelf(links);
                if (StringUtils.isBlank(first)) {
                    errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                        .modelClass(links.getClass())
                        .dataObject(links)
                        .errorMessage(String.format("%s does not have first link data", links))
                    );
                } else {
                    String firstLinkPageParam = getParameter(first, "page");
                    if (!"1".equals(firstLinkPageParam)) {
                        errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                            .modelClass(links.getClass())
                            .dataObject(links)
                            .errorMessage(String.format("first link %s does not have page param value as 1", first))
                        );
                    }
                    String firstLinkPageSizeParam = getParameter(first, "page-size");
                    if (pageSize.toString().equals(firstLinkPageParam)) {
                        errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                            .modelClass(links.getClass())
                            .dataObject(links)
                            .errorMessage(String.format("first link %s page-size param value %s does not match request page-size %s", first, firstLinkPageSizeParam, pageSize))
                        );
                    }
                }
                if (StringUtils.isBlank(last)) {
                    errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                        .modelClass(links.getClass())
                        .dataObject(links)
                        .errorMessage(String.format("%s does not have last link data", links))
                    );
                } else {
                    String lastLinkPageParam = getParameter(last, "page");
                    if (StringUtils.isBlank(lastLinkPageParam)) {
                        errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                            .modelClass(links.getClass())
                            .dataObject(links)
                            .errorMessage(String.format("last link %s does not have page param", last))
                        );
                    } else {
                        try {
                            Integer lastLinkPage = Integer.parseInt(lastLinkPageParam);
                            if (lastLinkPage < page) {
                                errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                                    .modelClass(links.getClass())
                                    .dataObject(links)
                                    .errorMessage(String.format("last link %s have invalid page param %s", last, lastLinkPage))
                                );
                            } else if (lastLinkPage.equals(page)) {
                                if (!StringUtils.isBlank(next)) {
                                    errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                                        .modelClass(links.getClass())
                                        .dataObject(links)
                                        .errorMessage(String.format("Next %s should be null as current page is the last page and there should be no next page", next))
                                    );
                                }
                            } else if (StringUtils.isBlank(next)) {
                                errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                                    .modelClass(links.getClass())
                                    .dataObject(links)
                                    .errorMessage(String.format("Next link should not be null as current page %d is not the last page and there should be next page", page))
                                );
                            }
                        } catch (NumberFormatException e) {
                            errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                                .modelClass(links.getClass())
                                .dataObject(links)
                                .errorMessage(String.format("last link %s does not have page param", last))
                            );
                        }
                    }
                    String lastLinkPageSizeParam = getParameter(last, "page-size");
                    if (pageSize.toString().equals(lastLinkPageParam)) {
                        errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                            .modelClass(links.getClass())
                            .dataObject(links)
                            .errorMessage(String.format("first link %s page-size param value %s does not match request page-size %s", last, lastLinkPageSizeParam, pageSize))
                        );
                    }
                }
                if (!requestUrl.equals(self)) {
                    errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                        .modelClass(links.getClass())
                        .dataObject(links)
                        .errorMessage(String.format("Self %s does not match request url %s", self, requestUrl))
                    );
                } else if (page == 1 && !StringUtils.isBlank(prev)) {
                    errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                        .modelClass(links.getClass())
                        .dataObject(links)
                        .errorMessage(String.format("Prev %s should be null as current page is the first page and there should be no prev page", prev))
                    );
                }
            }
            //TODO check prev and next link and check last against meta totalRecords and totalPages and refactoring
        }
        return errors;
    }

    private String getParameter(String url, String paramName) {
        String[] parts = url.split("\\?");
        if (parts.length < 2) return null;
        String queryString = parts[1];
        String[] queryParams = queryString.split("&");
        for (String queryParam : queryParams) {
            String[] keyValue = queryParam.split("=");
            if (keyValue[0].equalsIgnoreCase(paramName)) {
                if (keyValue.length < 2) return null;
                return keyValue[1];
            }
        }
        return null;
    }

    private String getFirst(LinksPaginated links) {
        Field firstField = FieldUtils.getField(links.getClass(), "first", true);
        return (String) ReflectionUtils.getField(firstField, links);
    }

    private String getPrev(LinksPaginated links) {
        Field prevField = FieldUtils.getField(links.getClass(), "prev", true);
        return (String) ReflectionUtils.getField(prevField, links);
    }

    private String getNext(LinksPaginated links) {
        Field nextField = FieldUtils.getField(links.getClass(), "next", true);
        return (String) ReflectionUtils.getField(nextField, links);
    }

    private String getLast(LinksPaginated links) {
        Field lastField = FieldUtils.getField(links.getClass(), "last", true);
        return (String) ReflectionUtils.getField(lastField, links);
    }

    private String getSelf(LinksPaginated links) {
        Field selfField = FieldUtils.getField(links.getClass(), "self", true);
        return (String) ReflectionUtils.getField(selfField, links);
    }

    private LinksPaginated getLinksPaginated(Object response) {
        Field linksField = FieldUtils.getField(response.getClass(), "links", true);
        return (LinksPaginated) ReflectionUtils.getField(linksField, response);
    }

    private MetaPaginated getMetaPaginated(Object response) {
        Field metaField = FieldUtils.getField(response.getClass(), "meta", true);
        return (MetaPaginated) ReflectionUtils.getField(metaField, response);
    }

    private void checkSelfLink(String requestUrl, Object response, List<ConformanceError> errors) {
        Field linksField = FieldUtils.getField(response.getClass(), "links", true);
        Object links = ReflectionUtils.getField(linksField, response);
        if (links == null) {
            errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                .errorField(linksField)
                .modelClass(response.getClass())
                .dataObject(response)
                .errorMessage(String.format("%s does not have links data", response))
            );
        } else {
            Field selfField = FieldUtils.getField(links.getClass(), "self", true);
            String selfLink = (String) ReflectionUtils.getField(selfField, links);
            if (!requestUrl.equals(selfLink)) {
                errors.add(new ConformanceError().errorType(ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA)
                    .errorField(selfField)
                    .modelClass(links.getClass())
                    .dataObject(links)
                    .errorMessage(String.format("Self %s does not match original request url %s", selfLink, requestUrl))
                );
            }
        }
    }
}
