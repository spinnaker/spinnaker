import { $q } from 'ngimport';
import React from 'react';
import { HandlerRendererResult, MenuRendererProps, Option, OptionValues, ReactSelectProps } from 'react-select';
import {
  BehaviorSubject,
  combineLatest as observableCombineLatest,
  from as observableFrom,
  of as observableOf,
  Subject,
} from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, takeUntil, tap } from 'rxjs/operators';

import { Application, HelpField, TetheredSelect, ValidationMessage } from '@spinnaker/core';
import { AwsImageReader, IAmazonImage } from '../../image';

export interface IAmazonImageSelectorProps {
  onChange: (value: IAmazonImage) => void;
  value: IAmazonImage;
  application: Application;
  credentials: string;
  region: string;
}

export interface IAmazonImageSelectorState {
  errorMessage?: string;
  selectionMode: 'packageImages' | 'searchAllImages';
  searchString: string;
  searchResults: IAmazonImage[];
  isSearching: boolean;
  packageImages: IAmazonImage[];
  isLoadingPackageImages: boolean;
}

type sortImagesByOptions = 'name' | 'ts';

export class AmazonImageSelectInput extends React.Component<IAmazonImageSelectorProps, IAmazonImageSelectorState> {
  public state: IAmazonImageSelectorState = {
    errorMessage: null,
    selectionMode: 'packageImages',
    searchString: '',
    searchResults: null,
    isSearching: false,
    packageImages: null,
    isLoadingPackageImages: true,
  };

  private awsImageReader = new AwsImageReader();
  private props$ = new Subject<IAmazonImageSelectorProps>();
  private searchInput$ = new Subject<string>();
  private destroy$ = new Subject();
  private sortImagesBy$ = new BehaviorSubject<sortImagesByOptions>('ts');

  public static makeFakeImage(imageName: string, imageId: string, region: string): IAmazonImage {
    if (!imageName && !imageId) {
      return null;
    }

    // assume that the specific image exists in the selected region
    const amis = { [region]: [imageId] };
    const attributes = { virtualizationType: '*', creationDate: new Date().toISOString() };

    return { imageName, amis, attributes } as IAmazonImage;
  }

  private loadImagesFromApplicationName(application: Application): PromiseLike<IAmazonImage[]> {
    const query = application.name.replace(/_/g, '[_\\-]') + '*';
    return this.awsImageReader.findImages({ q: query });
  }

  private buildQueryForSimilarImages(imageName: string) {
    let addDashToQuery = false;
    let packageBase = imageName.split('_')[0];
    const parts = packageBase.split('-');
    if (parts.length > 3) {
      packageBase = parts.slice(0, -3).join('-');
      addDashToQuery = true;
    }

    const tooShort = !packageBase || packageBase.length < 3;
    return tooShort ? null : packageBase + (addDashToQuery ? '-*' : '*');
  }

  private loadImageById(imageId: string, region: string, credentials: string): PromiseLike<IAmazonImage> {
    return !imageId ? $q.when(null) : this.awsImageReader.getImage(imageId, region, credentials).catch(() => null);
  }

  private searchForImages(query: string): PromiseLike<IAmazonImage[]> {
    const hasMinLength = query && query.length >= 3;
    return hasMinLength ? this.awsImageReader.findImages({ q: query }) : $q.when([]);
  }

  private fetchPackageImages(
    value: IAmazonImage,
    region: string,
    credentials: string,
    application: Application,
  ): PromiseLike<IAmazonImage[]> {
    const imageId = value && value.amis && value.amis[region] && value.amis[region][0];

    return this.loadImageById(imageId, region, credentials).then((image) => {
      if (!image) {
        return this.loadImagesFromApplicationName(application);
      }

      return this.searchForImages(this.buildQueryForSimilarImages(image.imageName)).then((similarImages) => {
        if (!similarImages.find((img) => img.imageName === image.imageName)) {
          // findImages has a limit of 1000 and may not always include the current image, which is confusing
          return similarImages.concat(image);
        }
        return similarImages;
      });
    });
  }

  private selectImage(selectedImage: IAmazonImage) {
    if (this.props.value !== selectedImage) {
      this.props.onChange(selectedImage);
    }
  }

  private findMatchingImage(images: IAmazonImage[], selectedImage: IAmazonImage) {
    return images.find((img) => selectedImage && selectedImage.imageName === img.imageName);
  }

  public componentDidMount() {
    const region$ = this.props$.pipe(
      map((x) => x.region),
      distinctUntilChanged(),
    );
    const { value, region, credentials, application } = this.props;

    this.setState({ isLoadingPackageImages: true });
    const fetchPromise = this.fetchPackageImages(value, region, credentials, application);

    const packageImages$ = observableFrom(fetchPromise).pipe(
      catchError((err) => {
        console.error(err);
        this.setState({ errorMessage: 'Unable to load package images' });
        return observableOf([] as IAmazonImage[]);
      }),
      tap(() => this.setState({ isLoadingPackageImages: false })),
    );

    const packageImagesInRegion$ = observableCombineLatest([packageImages$, region$, this.sortImagesBy$]).pipe(
      map(([packageImages, latestRegion, sortImagesBy]) => {
        const images = packageImages.filter((img) => !!img.amis[latestRegion]);
        return this.sortImages(images, sortImagesBy);
      }),
    );

    const searchString$ = this.searchInput$.pipe(
      tap((searchString) => this.setState({ searchString })),
      distinctUntilChanged(),
      debounceTime(250),
    );

    const searchImages$ = searchString$.pipe(
      tap(() => this.setState({ isSearching: true })),
      switchMap((searchString) => this.searchForImages(searchString)),
      catchError((err) => {
        console.error(err);
        this.setState({ errorMessage: 'Unable to search for images' });
        return observableOf([] as IAmazonImage[]);
      }),
      tap(() => this.setState({ isSearching: false })),
    );

    const searchImagesInRegion$ = observableCombineLatest([searchImages$, region$, this.sortImagesBy$]).pipe(
      map(([searchResults, latestRegion, sortImagesBy]) => {
        const { searchString } = this.state;
        // allow 'advanced' users to continue with just an ami id (backing image may not have been indexed yet)
        if (searchResults.length === 0 && !!/ami-[0-9a-f]{8,17}/.exec(searchString)) {
          const fakeImage = AmazonImageSelectInput.makeFakeImage(searchString, searchString, latestRegion);
          return [fakeImage].filter((x) => !!x);
        }

        // Filter down to only images which have an ami in the currently selected region
        const images = searchResults.filter((img) => !!img.amis[latestRegion]);
        return this.sortImages(images, sortImagesBy);
      }),
    );

    searchImagesInRegion$.pipe(takeUntil(this.destroy$)).subscribe((searchResults) => this.setState({ searchResults }));
    packageImagesInRegion$.pipe(takeUntil(this.destroy$)).subscribe((packageImages) => {
      this.setState({ packageImages });
      this.selectImage(this.findMatchingImage(packageImages, this.props.value));
    });

    // Clear out the selected image if the region changes and the image is not found in the new region
    region$
      .pipe(
        switchMap((selectedRegion) => {
          const image = this.props.value;
          if (this.state.selectionMode === 'packageImages') {
            // in packageImages mode, wait for the packageImages to load then find the matching one, or undefined
            return packageImagesInRegion$.pipe(map((images) => this.findMatchingImage(images, image)));
          } else {
            // in searchImages mode, return undefined if the selected image is not found in the new region
            const hasAmiInRegion = !!(image && image.amis && image.amis[selectedRegion]);
            return observableOf(hasAmiInRegion ? image : undefined);
          }
        }),
        takeUntil(this.destroy$),
      )
      .subscribe((image) => this.selectImage(image));
  }

  private setSortImagesBy(sortImagesBy: sortImagesByOptions) {
    this.sortImagesBy$.next(sortImagesBy);
  }

  private buildImageMenu = (params: MenuRendererProps): HandlerRendererResult => {
    const { ImageMenuHeading, ImageLabel } = this;
    const { options } = params;
    return (
      <div className="Select-menu-outer">
        <div className="Select-menu" role="listbox">
          {options.length > 0 && <ImageMenuHeading />}
          {options.map((o) => (
            <ImageLabel key={o.imageName} option={o} params={params} />
          ))}
        </div>
      </div>
    );
  };

  private ImageMenuHeading = () => {
    const sortImagesBy = this.sortImagesBy$.value;
    return (
      <div
        className="sp-padding-s-xaxis sp-padding-xs-yaxis small"
        style={{
          borderBottom: '1px solid var(--color-silver)',
          position: 'sticky',
          top: 0,
          backgroundColor: 'var(--color-white)',
        }}
      >
        <b>Sort by: </b>
        <a className="clickable sp-padding-xs-xaxis" onClick={() => this.setSortImagesBy('ts')}>
          {sortImagesBy === 'ts' ? <b>timestamp (newest first)</b> : 'timestamp (newest first)'}
        </a>
        <span> | </span>
        <a className="clickable sp-padding-xs-xaxis" onClick={() => this.setSortImagesBy('name')}>
          {sortImagesBy === 'name' ? <b>name (A-Z)</b> : 'name (A-Z)'}
        </a>
      </div>
    );
  };

  private sortImages(images: IAmazonImage[], sortImagesBy: sortImagesByOptions): IAmazonImage[] {
    return images.slice().sort((a, b) => {
      if (sortImagesBy === 'ts') {
        return b.attributes.creationDate.localeCompare(a.attributes.creationDate);
      }
      return a.imageName.localeCompare(b.imageName);
    });
  }

  private ImageLabel = (imageLabelProps: { option: Option<OptionValues>; params: MenuRendererProps }) => {
    const { credentials, region } = this.props;
    const { option, params } = imageLabelProps;
    const amiLabel =
      option.amis[region] && option.amis[region][0]
        ? option.amis[region][0]
        : ` - not found in ${credentials}/${region}`;
    return (
      <div
        key={option.imageName}
        onClick={() => params.selectValue(option)}
        onMouseOver={() => params.focusOption(option)}
        className={`Select-option ${
          params.focusedOption && params.focusedOption.imageName === option.imageName ? 'is-focused' : ''
        }`}
        role="option"
      >
        <div>{option.imageName}</div>
        <div className="small">
          <b>Created: </b>
          {option.attributes.creationDate}
          <b className="sp-padding-s-left">AMI: </b>
          {amiLabel}
        </div>
      </div>
    );
  };

  public componentDidUpdate() {
    this.props$.next(this.props);
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public render() {
    const { value, credentials, region, onChange } = this.props;
    const {
      isLoadingPackageImages,
      isSearching,
      selectionMode,
      packageImages,
      searchResults,
      searchString,
    } = this.state;
    const isPackageImagesLoaded = !!packageImages;

    const ImageOptionRenderer = (image: IAmazonImage) => {
      const amis = image.amis || {};
      const imageIdForSelectedRegion = amis[region] && amis[region][0];
      const message = imageIdForSelectedRegion
        ? `(${imageIdForSelectedRegion})`
        : ` - not found in ${credentials}/${region}`;

      return (
        <>
          <span>{image.imageName}</span>
          <span>{message}</span>
        </>
      );
    };

    const commonReactSelectProps: ReactSelectProps<any> = {
      clearable: false,
      required: true,
      valueKey: 'imageName',
      optionRenderer: ImageOptionRenderer,
      valueRenderer: ImageOptionRenderer,
      onSelectResetsInput: false,
      onBlurResetsInput: false,
      onCloseResetsInput: false,
      value,
    };

    const error = this.state.errorMessage ? <ValidationMessage message={this.state.errorMessage} type="error" /> : null;

    const noResultsText = `No results found in ${credentials}/${region}`;

    if (selectionMode === 'searchAllImages') {
      // User can search for any image using the typeahead
      // Results are streamed from the back end as the user types
      const lessThanThreeChars = !searchString || searchString.length < 3;
      const searchNoResultsText = lessThanThreeChars
        ? 'Please enter at least 3 characters'
        : isSearching
        ? 'Searching...'
        : noResultsText;

      return (
        <div className="col-md-9">
          {/* @ts-ignore */}
          <TetheredSelect
            {...commonReactSelectProps}
            menuRenderer={this.buildImageMenu}
            isLoading={isSearching}
            placeholder="Search for an image..."
            filterOptions={false as any}
            noResultsText={searchNoResultsText}
            options={searchResults}
            onInputChange={(searchInput) => {
              this.searchInput$.next(searchInput);
              return searchInput;
            }}
            onChange={onChange}
          />
          {error}
        </div>
      );
    } else if (isPackageImagesLoaded) {
      // User can pick an image from the preloaded 'packageImages' using the typeahead
      return (
        <div className="col-md-9">
          {/* @ts-ignore */}
          <TetheredSelect
            {...commonReactSelectProps}
            menuRenderer={this.buildImageMenu}
            isLoading={isLoadingPackageImages}
            placeholder="Pick an image"
            noResultsText={noResultsText}
            options={packageImages}
            onChange={onChange}
          />
          {error}
          <button type="button" className="link" onClick={() => this.setState({ selectionMode: 'searchAllImages' })}>
            Search All Images
          </button>{' '}
          <HelpField id="aws.serverGroup.allImages" />
        </div>
      );
    } else {
      // Show a disabled react-select while waiting for 'packageImages' to load
      return (
        <div className="col-md-9">
          {/* @ts-ignore */}
          <TetheredSelect
            {...commonReactSelectProps}
            isLoading={isLoadingPackageImages}
            disabled={true}
            options={[value].filter((x) => !!x)}
          />
          {error}
          <button type="button" className="link" onClick={() => this.setState({ selectionMode: 'searchAllImages' })}>
            Search All Images
          </button>{' '}
          <HelpField id="aws.serverGroup.allImages" />
        </div>
      );
    }
  }
}
