package io.pne.deploy.client.redmine.remote.data_model;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class CustomFields {
    int    id;
    String name;
    String value;
}
