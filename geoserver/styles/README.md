# GeoServer SLD Styles for Green Taxi Data

This directory contains Styled Layer Descriptor (SLD) files for styling Green Taxi trip data in GeoServer.

## Files

### `green-taxi-style.sld`
A basic style that colors points based on total fare amount:
- **Red**: High fare trips (> $50)
- **Orange**: Medium fare trips ($20-$50)
- **Green**: Low fare trips (<= $20)
- **Gray**: Trips without fare data

Includes labels showing the total amount and line styling for trip routes based on distance.

### `green-taxi-comprehensive.sld`
A comprehensive style that uses multiple attributes from the Green Taxi schema:
- **Payment Type**: Different shapes for credit card (square) vs cash (circle) payments
- **Trip Type**: Different symbols for street-hail (triangle) vs dispatch (star) trips
- **Passenger Count**: Size varies with number of passengers
- **Fare Amount**: Point size varies with total amount
- **Labels**: Shows total amount, trip distance, and passenger count

## Green Taxi Schema Fields

The SLD files are designed to work with the following fields from the Green Taxi Parquet schema:

- `VendorID` - Vendor identifier (1, 2, or 6)
- `lpep_pickup_datetime` - Pickup date/time
- `lpep_dropoff_datetime` - Dropoff date/time
- `store_and_fwd_flag` - Store and forward flag (Y/N)
- `RatecodeID` - Rate code (1-6, 99)
- `PULocationID` - Pickup location ID (Taxi Zone)
- `DOLocationID` - Dropoff location ID (Taxi Zone)
- `passenger_count` - Number of passengers
- `trip_distance` - Trip distance in miles
- `fare_amount` - Base fare amount
- `extra` - Extra charges
- `mta_tax` - MTA tax
- `tip_amount` - Tip amount
- `tolls_amount` - Tolls amount
- `improvement_surcharge` - Improvement surcharge
- `total_amount` - Total amount charged
- `payment_type` - Payment type (0-6)
- `trip_type` - Trip type (1=Street-hail, 2=Dispatch)
- `congestion_surcharge` - Congestion surcharge
- `cbd_congestion_fee` - CBD congestion fee

## Usage in GeoServer

1. **Upload the SLD file**:
   - Log into GeoServer web interface (http://localhost:8081/geoserver)
   - Navigate to Styles → Add a new style
   - Upload the `.sld` file

2. **Apply to a layer**:
   - Navigate to Layers → Select your Green Taxi layer
   - Edit the layer → Publishing tab
   - Select the style from the Default Style dropdown
   - Save

3. **For PostGIS data stores**:
   - Ensure your PostgreSQL database has the Green Taxi data with geometry columns
   - Create a PostGIS data store pointing to your database
   - Create a layer from the Green Taxi table
   - Apply the SLD style

## Customization

You can customize the styles by:
- Adjusting color values in the `<Fill>` and `<Stroke>` sections
- Modifying point sizes in the `<Size>` elements
- Changing filter conditions in the `<ogc:Filter>` sections
- Adding or removing rules based on your needs

## Notes

- The styles assume point or line geometries (points for pickup/dropoff locations, lines for trip routes)
- If your data doesn't have geometry, you'll need to join it with a spatial dataset (e.g., Taxi Zone polygons)
- The styles use OGC Filter expressions to categorize and style features based on attribute values
- Labels are configured to show at scales below 1:50,000 for better performance

