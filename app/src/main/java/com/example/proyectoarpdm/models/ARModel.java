package com.example.proyectoarpdm.models;

import java.util.ArrayList;
import java.util.List;

public class ARModel {
    private String id;
    private String name;
    private String modelUrl;
    private Object latitud;
    private Object longitud;
    private String imagen;
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

    public String getLatitud() { 
        return latitud != null ? String.valueOf(latitud) : ""; 
    }
    public void setLatitud(Object latitud) { this.latitud = latitud; }

    public String getLongitud() { 
        return longitud != null ? String.valueOf(longitud) : ""; 
    }
    public void setLongitud(Object longitud) { this.longitud = longitud; }

    public String getImagen() { return imagen; }
    public void setImagen(String imagen) { this.imagen = imagen; }

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
