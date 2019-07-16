package au.org.consumerdatastandards.conformance;

import net.serenitybdd.junit.runners.SerenityParameterizedRunner;
import net.thucydides.core.annotations.Steps;
import net.thucydides.core.util.EnvironmentVariables;
import net.thucydides.core.util.SystemEnvironmentVariables;
import net.thucydides.junit.annotations.UseTestDataFrom;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@UseTestDataFrom("testdata/banking-products-api-params.csv")
@RunWith(SerenityParameterizedRunner.class)
public class BankingProductsAPITest {

    private static String DEFAULT_API_BASE = "http://localhost:8080/cds-au/v1";

    private String effective;
    private String updatedSince;
    private String brand;
    private String productCategory;
    private Integer page;
    private Integer pageSize;

    @Steps
    BankingProductsAPISteps steps;

    @Before
    public void setApiBasePath() {
        EnvironmentVariables variables = SystemEnvironmentVariables.createEnvironmentVariables();
        String apiBasePath = variables.getProperty("apiBase");
        if (!StringUtils.isBlank(apiBasePath)) {
            steps.setupApiBasePath(apiBasePath);
        } else {
            steps.setupApiBasePath(DEFAULT_API_BASE);
        }
    }
    
    @Test
    public void listProducts() {
        steps.listProducts(effective, updatedSince, brand, productCategory, page, pageSize);
        steps.validateListProductsResponseStrict(effective, updatedSince, brand, productCategory, page, pageSize);
        steps.getProductIds();
    }    

    @Test
    public void listProductsAndGetProductDetail() {
        steps.listProducts(effective, updatedSince, brand, productCategory, page, pageSize);
        steps.validateListProductsResponse(effective, updatedSince, brand, productCategory, page, pageSize);
        List<String> productIds = steps.getProductIds();
        if (productIds != null && !productIds.isEmpty()) {
            for (String productId : productIds) {
                steps.getProductDetail(productId);
                steps.validateGetProductDetailResponse(productId);
            }
        }
    }

    public void setEffective(String effective) {
        this.effective = effective;
    }

    public void setUpdatedSince(String updatedSince) {
        this.updatedSince = updatedSince;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public void setProductCategory(String productCategory) {
        this.productCategory = productCategory;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
