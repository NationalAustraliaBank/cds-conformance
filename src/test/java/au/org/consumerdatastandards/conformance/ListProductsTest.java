package au.org.consumerdatastandards.conformance;

import net.serenitybdd.junit.runners.SerenityParameterizedRunner;
import net.thucydides.junit.annotations.UseTestDataFrom;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SerenityParameterizedRunner.class)
@UseTestDataFrom("testdata/banking-products-api-params.csv")
public class ListProductsTest extends BankingProductsAPITestBase {

    private String effective;
    private String updatedSince;
    private String brand;
    private String productCategory;
    private Integer page;
    private Integer pageSize;

    @Test
    public void listProducts() {
        steps.listProducts(effective, updatedSince, brand, productCategory, page, pageSize);
        steps.validateListProductsResponse(effective, updatedSince, brand, productCategory, page, pageSize);
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
