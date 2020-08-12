
import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.client.{Request, RestClient, RestClientBuilder}

class Hello {

  def connect: RestClient = {
    val credentials         = new UsernamePasswordCredentials("elastic", "changeme")
    val credentialsProvider = new BasicCredentialsProvider
    credentialsProvider.setCredentials(AuthScope.ANY, credentials)
    //prod
    RestClient
      .builder(new HttpHost("logsearch.dal.securustech.net", 82, "http"))
      .setHttpClientConfigCallback(
        new RestClientBuilder.HttpClientConfigCallback() {
          override def customizeHttpClient(httpClientBuilder: HttpAsyncClientBuilder): HttpAsyncClientBuilder =
            return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
        }
      )
      .build
  }

}
