package alien4cloud.model.orchestrators.locations;

import java.util.List;

import com.google.common.collect.Lists;

import alien4cloud.orchestrators.locations.services.LocationResourceTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ApiModel("Contains the types and templates of elements configured for a given location.")
public class LocationResources extends LocationResourceTypes {
    @ApiModelProperty(value = "List of configuration templates already configured for the location. Usually abstract types.")
    private List<LocationResourceTemplate> configurationTemplates = Lists.newArrayList();
    @ApiModelProperty(value = "List of node templates already configured for the location.")
    private List<LocationResourceTemplate> nodeTemplates = Lists.newArrayList();
    @ApiModelProperty(value = "List of policies templates already configured for the location.")
    private List<PolicyLocationResourceTemplate> policyTemplates = Lists.newArrayList();

    public LocationResources(LocationResourceTypes locationResourceTypes) {
        super(locationResourceTypes);
    }

    public void addAll(LocationResourceTypes locationResourceTypes) {
        getConfigurationTypes().putAll(locationResourceTypes.getConfigurationTypes());
        getNodeTypes().putAll(locationResourceTypes.getNodeTypes());
        getDataTypes().putAll(locationResourceTypes.getDataTypes());
        getCapabilityTypes().putAll(locationResourceTypes.getCapabilityTypes());
        getAllNodeTypes().putAll(locationResourceTypes.getAllNodeTypes());
        getOnDemandTypes().putAll(locationResourceTypes.getOnDemandTypes());
        getPolicyTypes().putAll(locationResourceTypes.getPolicyTypes());
    }
}