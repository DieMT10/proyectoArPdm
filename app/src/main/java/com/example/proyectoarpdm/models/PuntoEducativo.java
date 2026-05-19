package com.example.proyectoarpdm.models;

public class PuntoEducativo {

    private String nombre;
    private String descripcion;
    private double latitud;
    private double longitud;
    private String modelo3D;
    private String imagenReferencia;

    public PuntoEducativo() {
    }

    public PuntoEducativo(String nombre, String descripcion,
                          double latitud, double longitud,
                          String modelo3D, String imagenReferencia) {

        this.nombre = nombre;
        this.descripcion = descripcion;
        this.latitud = latitud;
        this.longitud = longitud;
        this.modelo3D = modelo3D;
        this.imagenReferencia = imagenReferencia;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public double getLatitud() {
        return latitud;
    }

    public double getLongitud() {
        return longitud;
    }

    public String getModelo3D() {
        return modelo3D;
    }

    public String getImagenReferencia() {
        return imagenReferencia;
    }
}