# Zbynek GPS Utilitilies - zbynek-gps-tool command line utility

Command line utility to manipulate GPX files.


## Download

- https://github.com/kvr000/zbynek-gps/releases/download/master/zbynek-gps-tool
- https://github.com/kvr000/zbynek-gps/releases/tag/master


## Usage

```
zbynek-gps-tool [options] subcommand [options] arguments
```


## retrack

```
zbynek-gps-tool retrack -o output.gpx --position-prio 0,1,2 --elevation-prio 1,2,0 input.gpx another.gpx more.gpx ...
```

The command detects periods when device did not have GPS signal and stored the
same position over period of time.  For such periods, it linearly interpolates
the values based on latest prior known location and earliest later known
location.

This allows various utilities such as Strava to store and show correct distance
and show all performance information for the periods missing location.

Example: https://www.strava.com/activities/7637073707/analysis : This was a
travel from Czech Republic to Canada where GPS was lost most of the time during
flight and even a while later.  While the route is obviously slightly wrong
(going straight instead of close to pole), the performance data was retained.


## concat

```
zbynek-gps-tool concat -o output.gpx one.gpx two.gpx three.gpx
```

The command concatenates multiple files into one, with the priority of data
given by order of arguments.  The command automatically orders the points by
time, no matter what is the order of parameters.


## cut

```
zbynek-gps-tool cut -o output.gpx -s 2022-08-14T18:12:01Z -e 2022-08-14T18:15:16Z
```

The command removes period specified by `-s` and `-e` parameters (inclusive)
from the given gpx file and write it to the same file.


## find

```
zbynek-gps-tool find --source-strava-csv activities.csv --since 2022-01-01T00:00:00Z --find-point -123.004850,49.233800,50 --group-found-time HH:mm
```

Finds specific point (within radius) in set of files and prints the files and time or groups by time.

### Options:
- `--source-dir directory` : read files from the directory
- `--source-strava-csv file` : read files from Strava activities.csv
- `--since time` : filters by activity start time being higher inclusive (YYYY-MM-DDTHH:mm:ssZ)
- `--till time` : filters by activity start time being lower exclusive (YYYY-MM-DDTHH:mm:ssZ)
- `--find-point lat,lon,radius:...` : find one of the points with radius distance
- `--print-id-and-found-time time-format` : prints id and found local time
- `--group-found-time time-format` : groups and prints found time
- `--export-gpx directory` : exports found files to directory/id.gpx files
- `--remove-privacy-zone lat,lon,radius` : removes privacy zone from output


## fit-to-gps

```
zbynek-gps-tool -o output fit-to-gpx source
zbynek-gps-tool fit-to-gpx --batch sources...
```

The command converts FIT files to GPX files.


## Build

You need to install:
- java (at least version 11)
- maven

Debian or Ubuntu:
```
sudo apt -y install maven
```

MacOs:
```
brew install maven
```

RedHat or Suse:
```
sudo yum -y install maven
```

Build:
```
git clone https://github.com/kvr000/zbynek-gps.git
cd zbynek-gps/
mvn -f zbynek-gps-tool/ package

./zbynek-gps-tool/target/zbynek-gps-tool -h
```


## Caveats

### Interpolation precision

The location interpolation is based on interpolating longitude and latitude
separately.  This works for short distances, it works less for longer distance
(thousands of kilometers).


## License

The code is released under version 2.0 of the [Apache License][].

## Stay in Touch

Feel free to contact me at kvr000@gmail.com  and http://github.com/kvr000/ and http://github.com/kvr000/zbynek-gps/

[Apache License]: http://www.apache.org/licenses/LICENSE-2.0
