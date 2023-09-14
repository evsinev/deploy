package io.pne.deploy.client.redmine.remote.data_model;

import lombok.Data;

@Data
public class CustomFields {
    private int id;
    private String name;
    private String value;
}
