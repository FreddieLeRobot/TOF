using CommunityToolkit.Mvvm.ComponentModel;
using LiveChartsCore.Defaults;
using LiveChartsCore.SkiaSharpView;
using LiveChartsCore;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System;
using System.Collections.Generic;
using System.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using LiveChartsCore.SkiaSharpView.Painting;
using SkiaSharp;
using LiveChartsCore.SkiaSharpView.Painting.Effects;

namespace ViewModelsSamples.Lines.AutoUpdate;

[ObservableObject]
public partial class ViewModel
{
    private readonly Random _random = new();
    private readonly ObservableCollection<ObservablePoint> _observableValues;

    public ViewModel()
    {
        // Use ObservableCollections to let the chart listen for changes (or any INotifyCollectionChanged). 
        _observableValues = new ObservableCollection<ObservablePoint>
            {
                // Use the ObservableValue or ObservablePoint types to let the chart listen for property changes 
                // or use any INotifyPropertyChanged implementation 
                new ObservablePoint(DateTime.Now.ToFileTime(),0),
            };


        Series = new ObservableCollection<ISeries>
            {
                new LineSeries<ObservablePoint>
                {
                    Values = _observableValues,
                    Stroke = new SolidColorPaint(SKColors.WhiteSmoke) { StrokeThickness = 2 },
                    GeometryStroke = null,
                    GeometrySize = 0,
                    Fill = null
                }
            };


        // in the following sample notice that the type int does not implement INotifyPropertyChanged
        // and our Series.Values property is of type List<T>
        // List<T> does not implement INotifyCollectionChanged
        // this means the following series is not listening for changes.
        // Series.Add(new ColumnSeries<int> { Values = new List<int> { 2, 4, 6, 1, 7, -2 } }); 
    }
    public Axis[] XAxes { get; set; } =
    {
        new Axis
        {
            Labeler = value => new DateTime((long) value).ToString("MM/dd hh:mm:ss:ff"),
            LabelsRotation = 20,
            
            Name = "Time",
            NamePaint = new SolidColorPaint(SKColors.GhostWhite),

            LabelsPaint = new SolidColorPaint(SKColors.WhiteSmoke),
            TextSize = 15,

            //MaxLimit = 8000,
            //MinLimit = 20, // Set zoom when a COM port handshake is called.

            //LabelsRotation = 15,
            //Labeler = value => new DateTime((long)value).ToString("yyyy MMM dd"),
            // set the unit width of the axis to "days"
            // since our X axis is of type date time and 
            // the interval between our points is in days
            UnitWidth = TimeSpan.FromDays(1).Ticks,

            SeparatorsPaint = new SolidColorPaint(SKColors.LightSlateGray)
            {
                StrokeThickness = 2,
                PathEffect = new DashEffect(new float[] { 3, 3 })
            },

            // when using a date time type, let the library know your unit 

            // if the difference between our points is in hours then we would:
            // UnitWidth = TimeSpan.FromHours(1).Ticks,

            // since all the months and years have a different number of days
            // we can use the average, it would not cause any visible error in the user interface
            // Months: TimeSpan.FromDays(30.4375).Ticks
            // Years: TimeSpan.FromDays(365.25).Ticks

            // The MinStep property forces the separator to be greater than 1 day.
            //MinStep = TimeSpan.FromDays(1).Ticks
        }
    };
    public ObservableCollection<ISeries> Series { get; set; }
    [RelayCommand]
    public void AddItem(float item)
    {
        ObservablePoint point = new ObservablePoint(DateTime.Now.ToFileTime(), item);
        _observableValues.Add(point);
    }
    [RelayCommand]
    public void RemoveItem()
    {
        if (_observableValues.Count == 0) return;
        _observableValues.RemoveAt(0);
    }

    [RelayCommand]
    public void RemoveSeries()
    {

        _observableValues.Clear();
    }
    public int length()
    {
        return _observableValues.Count(); 
    }

    public void scroll(float item)
    {
        _observableValues.RemoveAt(0);
        ObservablePoint point = new ObservablePoint(DateTime.Now.ToFileTime(), item);
        _observableValues.Add(point);

    }
}

