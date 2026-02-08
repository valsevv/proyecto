package com.example.proyect.game;

/**
 * Server-side state for a single drone.
 */
public class DroneState {

    private double x;
    private double y;

    public DroneState(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
}
