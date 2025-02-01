package edu.northeastern.csye6225.webapp.config;
import com.timgroup.statsd.StatsDClient;
import edu.northeastern.csye6225.webapp.filter.GetRequestAuthFilter;
import edu.northeastern.csye6225.webapp.filter.ResponseFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
public class FilterConfig {

    @Autowired
    private DataSource source;

    @Autowired
    private StatsDClient statsDClient;

    @Bean
    public FilterRegistrationBean<GetRequestAuthFilter> registerGetRequestAuthFilter() {
        FilterRegistrationBean<GetRequestAuthFilter> registrationBean = new FilterRegistrationBean<>();
        //registrationBean.setFilter(new GetRequestAuthFilter());
        registrationBean.setFilter(new GetRequestAuthFilter(statsDClient));
        registrationBean.addUrlPatterns("/*"); // Apply to all URL patterns or specify a path
        registrationBean.setOrder(1);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<ResponseFilter> headerFilter(){
        FilterRegistrationBean<ResponseFilter> filter = new FilterRegistrationBean<>();
        //filter.setFilter(new ResponseFilter());
        filter.setFilter(new ResponseFilter(statsDClient));
        filter.addUrlPatterns("/*");
        filter.setOrder(2);
        return  filter;
    }


}