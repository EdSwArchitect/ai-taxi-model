<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0" 
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd" 
    xmlns="http://www.opengis.net/sld" 
    xmlns:ogc="http://www.opengis.net/ogc" 
    xmlns:xlink="http://www.w3.org/1999/xlink" 
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>green_taxi_comprehensive_style</Name>
    <UserStyle>
      <Title>Green Taxi Comprehensive Style</Title>
      <Abstract>Comprehensive style for Green Taxi (SHL) trip data matching all Parquet schema fields:
        VendorID, lpep_pickup_datetime, lpep_dropoff_datetime, store_and_fwd_flag, RatecodeID,
        PULocationID, DOLocationID, passenger_count, trip_distance, fare_amount, extra, mta_tax,
        tip_amount, tolls_amount, improvement_surcharge, total_amount, payment_type, trip_type,
        congestion_surcharge, cbd_congestion_fee</Abstract>
      
      <FeatureTypeStyle>
        
        <!-- Rule: Style by payment type -->
        <Rule>
          <Name>Credit Card Payment</Name>
          <Title>Credit Card Payment (Type 1)</Title>
          <ogc:Filter>
            <ogc:PropertyIsEqualTo>
              <ogc:PropertyName>payment_type</ogc:PropertyName>
              <ogc:Literal>1</ogc:Literal>
            </ogc:PropertyIsEqualTo>
          </ogc:Filter>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>square</WellKnownName>
                <Fill>
                  <CssParameter name="fill">#0066CC</CssParameter>
                  <CssParameter name="fill-opacity">0.8</CssParameter>
                </Fill>
                <Stroke>
                  <CssParameter name="stroke">#003366</CssParameter>
                  <CssParameter name="stroke-width">1.5</CssParameter>
                </Stroke>
              </Mark>
              <Size>
                <ogc:Add>
                  <ogc:Mul>
                    <ogc:PropertyName>total_amount</ogc:PropertyName>
                    <ogc:Literal>0.2</ogc:Literal>
                  </ogc:Mul>
                  <ogc:Literal>5</ogc:Literal>
                </ogc:Add>
              </Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
        
        <Rule>
          <Name>Cash Payment</Name>
          <Title>Cash Payment (Type 2)</Title>
          <ogc:Filter>
            <ogc:PropertyIsEqualTo>
              <ogc:PropertyName>payment_type</ogc:PropertyName>
              <ogc:Literal>2</ogc:Literal>
            </ogc:PropertyIsEqualTo>
          </ogc:Filter>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>circle</WellKnownName>
                <Fill>
                  <CssParameter name="fill">#FF9900</CssParameter>
                  <CssParameter name="fill-opacity">0.8</CssParameter>
                </Fill>
                <Stroke>
                  <CssParameter name="stroke">#CC6600</CssParameter>
                  <CssParameter name="stroke-width">1.5</CssParameter>
                </Stroke>
              </Mark>
              <Size>
                <ogc:Add>
                  <ogc:Mul>
                    <ogc:PropertyName>total_amount</ogc:PropertyName>
                    <ogc:Literal>0.2</ogc:Literal>
                  </ogc:Mul>
                  <ogc:Literal>5</ogc:Literal>
                </ogc:Add>
              </Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
        
        <!-- Rule: Style by trip type (Street-hail vs Dispatch) -->
        <Rule>
          <Name>Street Hail</Name>
          <Title>Street Hail (Type 1)</Title>
          <ogc:Filter>
            <ogc:And>
              <ogc:PropertyIsEqualTo>
                <ogc:PropertyName>trip_type</ogc:PropertyName>
                <ogc:Literal>1</ogc:Literal>
              </ogc:PropertyIsEqualTo>
              <ogc:Or>
                <ogc:PropertyIsNotEqualTo>
                  <ogc:PropertyName>payment_type</ogc:PropertyName>
                  <ogc:Literal>1</ogc:Literal>
                </ogc:PropertyIsNotEqualTo>
                <ogc:PropertyIsNotEqualTo>
                  <ogc:PropertyName>payment_type</ogc:PropertyName>
                  <ogc:Literal>2</ogc:Literal>
                </ogc:PropertyIsNotEqualTo>
              </ogc:Or>
            </ogc:And>
          </ogc:Filter>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>triangle</WellKnownName>
                <Fill>
                  <CssParameter name="fill">#00CC00</CssParameter>
                  <CssParameter name="fill-opacity">0.7</CssParameter>
                </Fill>
                <Stroke>
                  <CssParameter name="stroke">#009900</CssParameter>
                  <CssParameter name="stroke-width">1</CssParameter>
                </Stroke>
              </Mark>
              <Size>10</Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
        
        <Rule>
          <Name>Dispatch</Name>
          <Title>Dispatch (Type 2)</Title>
          <ogc:Filter>
            <ogc:And>
              <ogc:PropertyIsEqualTo>
                <ogc:PropertyName>trip_type</ogc:PropertyName>
                <ogc:Literal>2</ogc:Literal>
              </ogc:PropertyIsEqualTo>
              <ogc:Or>
                <ogc:PropertyIsNotEqualTo>
                  <ogc:PropertyName>payment_type</ogc:PropertyName>
                  <ogc:Literal>1</ogc:Literal>
                </ogc:PropertyIsNotEqualTo>
                <ogc:PropertyIsNotEqualTo>
                  <ogc:PropertyName>payment_type</ogc:PropertyName>
                  <ogc:Literal>2</ogc:Literal>
                </ogc:PropertyIsNotEqualTo>
              </ogc:Or>
            </ogc:And>
          </ogc:Filter>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>star</WellKnownName>
                <Fill>
                  <CssParameter name="fill">#9900CC</CssParameter>
                  <CssParameter name="fill-opacity">0.7</CssParameter>
                </Fill>
                <Stroke>
                  <CssParameter name="stroke">#660099</CssParameter>
                  <CssParameter name="stroke-width">1</CssParameter>
                </Stroke>
              </Mark>
              <Size>10</Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
        
        <!-- Rule: Style by passenger count -->
        <Rule>
          <Name>Multiple Passengers</Name>
          <Title>Multiple Passengers (&gt; 1)</Title>
          <ogc:Filter>
            <ogc:And>
              <ogc:PropertyIsGreaterThan>
                <ogc:PropertyName>passenger_count</ogc:PropertyName>
                <ogc:Literal>1</ogc:Literal>
              </ogc:PropertyIsGreaterThan>
              <ogc:Or>
                <ogc:PropertyIsNotEqualTo>
                  <ogc:PropertyName>payment_type</ogc:PropertyName>
                  <ogc:Literal>1</ogc:Literal>
                </ogc:PropertyIsNotEqualTo>
                <ogc:PropertyIsNotEqualTo>
                  <ogc:PropertyName>payment_type</ogc:PropertyName>
                  <ogc:Literal>2</ogc:Literal>
                </ogc:PropertyIsNotEqualTo>
              </ogc:Or>
              <ogc:Or>
                <ogc:PropertyIsNotEqualTo>
                  <ogc:PropertyName>trip_type</ogc:PropertyName>
                  <ogc:Literal>1</ogc:Literal>
                </ogc:PropertyIsNotEqualTo>
                <ogc:PropertyIsNotEqualTo>
                  <ogc:PropertyName>trip_type</ogc:PropertyName>
                  <ogc:Literal>2</ogc:Literal>
                </ogc:PropertyIsNotEqualTo>
              </ogc:Or>
            </ogc:And>
          </ogc:Filter>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>circle</WellKnownName>
                <Fill>
                  <CssParameter name="fill">#FF00FF</CssParameter>
                  <CssParameter name="fill-opacity">0.6</CssParameter>
                </Fill>
                <Stroke>
                  <CssParameter name="stroke">#CC00CC</CssParameter>
                  <CssParameter name="stroke-width">2</CssParameter>
                </Stroke>
              </Mark>
              <Size>
                <ogc:Mul>
                  <ogc:PropertyName>passenger_count</ogc:PropertyName>
                  <ogc:Literal>3</ogc:Literal>
                </ogc:Mul>
              </Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
        
        <!-- Default rule -->
        <Rule>
          <Name>Default</Name>
          <Title>Other Trips</Title>
          <ElseFilter/>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>circle</WellKnownName>
                <Fill>
                  <CssParameter name="fill">#CCCCCC</CssParameter>
                  <CssParameter name="fill-opacity">0.5</CssParameter>
                </Fill>
                <Stroke>
                  <CssParameter name="stroke">#666666</CssParameter>
                  <CssParameter name="stroke-width">1</CssParameter>
                </Stroke>
              </Mark>
              <Size>6</Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
        
        <!-- Label with multiple attributes -->
        <Rule>
          <Name>Labels</Name>
          <MaxScaleDenominator>50000</MaxScaleDenominator>
          <TextSymbolizer>
            <Label>
              <ogc:Function name="Concatenate">
                <ogc:Literal>$</ogc:Literal>
                <ogc:Function name="numberFormat">
                  <ogc:Literal>#.##</ogc:Literal>
                  <ogc:PropertyName>total_amount</ogc:PropertyName>
                </ogc:Function>
                <ogc:Literal>&#10;</ogc:Literal>
                <ogc:PropertyName>trip_distance</ogc:PropertyName>
                <ogc:Literal> mi</ogc:Literal>
                <ogc:Literal>&#10;</ogc:Literal>
                <ogc:PropertyName>passenger_count</ogc:PropertyName>
                <ogc:Literal> pax</ogc:Literal>
              </ogc:Function>
            </Label>
            <Font>
              <CssParameter name="font-family">Arial</CssParameter>
              <CssParameter name="font-size">9</CssParameter>
              <CssParameter name="font-style">normal</CssParameter>
              <CssParameter name="font-weight">bold</CssParameter>
            </Font>
            <LabelPlacement>
              <PointPlacement>
                <AnchorPoint>
                  <AnchorPointX>0.5</AnchorPointX>
                  <AnchorPointY>0.5</AnchorPointY>
                </AnchorPoint>
                <Displacement>
                  <DisplacementX>0</DisplacementX>
                  <DisplacementY>20</DisplacementY>
                </Displacement>
              </PointPlacement>
            </LabelPlacement>
            <Fill>
              <CssParameter name="fill">#000000</CssParameter>
            </Fill>
            <Halo>
              <Radius>2</Radius>
              <Fill>
                <CssParameter name="fill">#FFFFFF</CssParameter>
                <CssParameter name="fill-opacity">0.8</CssParameter>
              </Fill>
            </Halo>
            <VendorOption name="autoWrap">80</VendorOption>
            <VendorOption name="maxDisplacement">50</VendorOption>
          </TextSymbolizer>
        </Rule>
        
      </FeatureTypeStyle>
      
      <!-- Feature Type Style for line features (trip routes) -->
      <FeatureTypeStyle>
        <Rule>
          <Name>Trip Routes</Name>
          <Title>Trip Routes</Title>
          <LineSymbolizer>
            <Stroke>
              <CssParameter name="stroke">#00AA00</CssParameter>
              <CssParameter name="stroke-width">
                <ogc:Function name="Categorize">
                  <ogc:PropertyName>trip_distance</ogc:PropertyName>
                  <ogc:Literal>1</ogc:Literal>
                  <ogc:Literal>0</ogc:Literal>
                  <ogc:Literal>1</ogc:Literal>
                  <ogc:Literal>2</ogc:Literal>
                  <ogc:Literal>5</ogc:Literal>
                  <ogc:Literal>3</ogc:Literal>
                  <ogc:Literal>10</ogc:Literal>
                  <ogc:Literal>4</ogc:Literal>
                </ogc:Function>
              </CssParameter>
              <CssParameter name="stroke-opacity">0.6</CssParameter>
              <CssParameter name="stroke-dasharray">5 2</CssParameter>
            </Stroke>
          </LineSymbolizer>
        </Rule>
      </FeatureTypeStyle>
      
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>

