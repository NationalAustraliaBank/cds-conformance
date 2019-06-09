package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.api.banking.models.ResponseBankingProductList;
import net.serenitybdd.junit.runners.SerenityParameterizedRunner;
import net.thucydides.core.annotations.Steps;
import net.thucydides.junit.annotations.UseTestDataFrom;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@UseTestDataFrom("testdata/banking-products-api-params.csv")
@RunWith(SerenityParameterizedRunner.class)
public class BankingProductsAPITest {

    private String effective;
    private String updatedSince;
    private String brand;
    private String productCategory;
    private Integer page;
    private Integer pageSize;

    @Steps
    BankingProductsAPISteps steps;

    @Test
    public void listAndGet() {
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
