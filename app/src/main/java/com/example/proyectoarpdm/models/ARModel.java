package com.example.proyectoarpdm.models;

import java.util.ArrayList;
import java.util.List;

public class ARModel {
    private String id;
    private String name;
    private String modelUrl;
    private String latitud;
    private String longitud;
    private Object slides; // Cambiado a Object para evitar errores de tipo en Firebase
    private String slidesUrl;

    public ARModel() {
        // Required for Firebase
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getModelUrl() { return modelUrl; }
    public void setModelUrl(String modelUrl) { this.modelUrl = modelUrl; }

    public String getLatitud() { return latitud; }
    public void setLatitud(String latitud) { this.latitud = latitud; }

    public String getLongitud() { return longitud; }
    public void setLongitud(String longitud) { this.longitud = longitud; }

    public List<String> getSlides() {
        if (slides instanceof List) {
            return (List<String>) slides;
        } else if (slides instanceof String) {
            List<String> list = new ArrayList<>();
            String s = (String) slides;
            if (!s.isEmpty()) {
                list.add(s);
            }
            return list;
        }
        return new ArrayList<>();
    }
    
    public void setSlides(Object slides) { this.slides = slides; }

    public String getSlidesUrl() { return slidesUrl; }
    public void setSlidesUrl(String slidesUrl) { this.slidesUrl = slidesUrl; }
}
