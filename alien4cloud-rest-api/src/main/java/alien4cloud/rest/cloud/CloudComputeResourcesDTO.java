package alien4cloud.rest.cloud;

import java.util.Collection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import alien4cloud.model.cloud.ActivableComputeTemplate;
import alien4cloud.model.cloud.MatchedCloudImage;
import alien4cloud.model.cloud.MatchedCloudImageFlavor;

import com.fasterxml.jackson.annotation.JsonInclude;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuppressWarnings("PMD.UnusedPrivateField")
public class CloudComputeResourcesDTO {

    /**
     * The compute templates that are available for this cloud
     */
    private Collection<ActivableComputeTemplate> computeTemplates;

    /**
     * The matching configuration for image and flavor
     */
    private Collection<MatchedCloudImage> matchedCloudImages;

    private Collection<MatchedCloudImageFlavor> matchedCloudImageFlavors;
}
